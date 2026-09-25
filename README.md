# ⚔️ GitHub Battle

A Java 17 / Spring Boot 4 REST API that compares two GitHub users with transparent, category-by-category scoring. The frontend will be developed separately.

## Run locally

Install JDK 17 or newer and set `JAVA_HOME`. The Maven wrapper downloads Maven and dependencies on its first run.

```powershell
# Windows
./mvnw.cmd spring-boot:run
```

```sh
# macOS / Linux
./mvnw spring-boot:run
```

The server listens on `http://localhost:8081`. Set `PORT` to override it. No database or Redis service is required.

Optionally copy `.env.example` to `.env` and set `GITHUB_TOKEN`, or supply that environment variable directly. Never commit a token. Without a token, the API compares followers, repositories, and stars. A valid token enables GraphQL commit and streak metrics. Use a token with public access only: contribution data reflects what the server token can see, so private repository permissions can affect the returned metrics.

## API

```sh
curl "http://localhost:8081/api/battles?left=octocat&right=torvalds"
```

Both query parameters are required. Names are trimmed and compared without case sensitivity. Invalid names, organizations, and comparing a user with themselves are rejected.

For example, `/api/battles?left=emanuel&right=walgidio` means:

| URL part | Meaning |
| --- | --- |
| `/api/battles` | Comparison resource handled by `BattleController` |
| `?` | Start of the query parameters |
| `left=emanuel` | First GitHub username |
| `&` | Separator between parameters |
| `right=walgidio` | Second GitHub username |

`?emanuel&walgidio` is invalid because it does not supply the named `left` and `right` parameters. Usernames identify GitHub accounts, not display names.

There is one public operation today: `GET /api/battles`. It reads current statistics and does not create a stored battle. One controller is sufficient for this resource. If saved battles are added later, they can introduce `POST /api/battles` and `GET /api/battles/{id}`; those routes do not exist yet.

The response includes:

- `left` and `right`: usernames, names, profile/avatar links, raw statistics, and fetch timestamps.
- `categories`: raw values, base weights, availability, and points awarded to each player.
- `leftScore` and `rightScore`: scores out of 100, rounded to two decimals.
- `winner`: the winning username, or `null` for a tie; `tie` is also explicit.
- `notes`: contribution-window and missing-data explanations.
- `sharePath`: a relative URL that reruns the comparison. This is a live comparison, not a saved snapshot; results can change.

The complete contract is in [docs/openapi.yaml](docs/openapi.yaml). Import it into an OpenAPI-compatible API client or documentation viewer.

## Scoring

| Category | Weight | Definition |
| --- | ---: | --- |
| Followers | 20 | Current follower count |
| Repositories | 15 | Owned public repository count, including forks |
| Stars | 30 | Sum of stars across owned public repositories, including forks |
| Commits | 25 | GitHub contribution-counted commits in its default past-year window |
| Longest streak | 10 | Longest consecutive run of active contribution-calendar days in that window |

Each category splits its points proportionally: `leftPoints = weight × leftValue / (leftValue + rightValue)`. Two zero values split the points equally. Scores are normalized by the total available weight. An unavailable metric is excluded for both players rather than treated as zero. The winner is determined from the displayed scores; displayed ties have no winner.

Contribution metrics follow GitHub's contribution rules; they are not lifetime commit totals or a measure of developer ability. Calendar activity includes more than commits and may include private contribution counts that the account shares. See the [GitHub contribution schema](https://docs.github.com/en/graphql/reference/users) and [repository API](https://docs.github.com/en/rest/repos/repos#list-repositories-for-a-user).

## Configuration and operational limits

| Setting | Default | Purpose |
| --- | --- | --- |
| `PORT` | `8081` | HTTP port |
| `GITHUB_TOKEN` | empty | Optional server-side GitHub token |
| `github.base-url` | `https://api.github.com` | Upstream URL; override for local integration fixtures |
| `github.max-repository-pages` | `100` | Maximum pages per profile, between 1 and 100 |
| `github.connect-timeout` | `5s` | Connection timeout per GitHub request |
| `github.read-timeout` | `15s` | Read timeout per GitHub request |

Spring configuration settings can also be supplied as command-line arguments, for example `--github.read-timeout=10s`. Timeouts must be between 1ms and 60s.

Stars are fetched with 100 repositories per page. Accounts exceeding the page limit fail explicitly instead of receiving a truncated score. Successful profiles are cached in memory for five minutes (up to 1,000 profiles), retaining their original fetch timestamps. Concurrent requests for the same profile share one load; errors are not cached. Both profile waits share a 30-second comparison deadline (504 on expiry). Up to four upstream loads run concurrently with no queue; excess distinct loads return 503. A timed-out caller does not cancel shared work: an in-progress HTTP call may finish under its existing socket timeouts, but no further calls start after the load budget expires. There are no automatic retries.

Each instance admits a burst of 60 comparison requests and replenishes one token per second, with a maximum of eight simultaneous comparisons. Excess traffic returns 429 with Retry-After; concurrent capacity exhaustion returns 503. These controls and the cache are per process, not distributed. Keep reverse-proxy traffic limits for public deployments, particularly when scaling to multiple instances. Without a GitHub token, even this request budget can exceed GitHub quota.

Set FRONTEND_ORIGIN to the exact browser origin (for example https://your-frontend.example, without a trailing slash) to enable CORS. It defaults to disabled; credentials and wildcard origins are not enabled.

## Errors

Failures use `application/problem+json`, with `status`, `title`, and a readable `detail`. Raw GitHub responses and tokens are not returned.

| HTTP status | Meaning |
| --- | --- |
| 400 | Missing/invalid names, same user, or an organization |
| 404 | GitHub user not found |
| 422 | Repository lookup limit exceeded |
| 429 | Per-instance request budget exceeded; Retry-After specifies a suggested delay |
| 502 | Invalid server token, unavailable/invalid upstream data, or network failure |
| 503 | GitHub rate limit, access restriction, or server concurrency capacity exhausted |
| 504 | Comparison deadline exceeded |

## Test and package

```powershell
./mvnw.cmd verify
java -jar target/github-battle-0.0.1-SNAPSHOT.jar
```

Use `./mvnw verify` on macOS/Linux. Tests use an in-process HTTP fixture and require no real token or live GitHub connection. They cover scoring, ties, missing metrics, validation, REST serialization, pagination, contribution streaks, upstream failures, and startup. GitHub Actions runs verification on pushes and pull requests.

## Contribute

Read [CONTRIBUTING.md](CONTRIBUTING.md) for the package boundaries, local workflow, and pull request expectations. Configuration is in `src/main/resources/application.yaml`. The API is stateless; immutable saved results and the separate frontend are future work.

The production source structure is:

```text
com.github.battle.app
├── GithubBattleApplication.java
├── controller/   # HTTP endpoints
├── dto/          # Response contracts
├── service/      # Comparison rules and profile-provider interface
├── client/       # GitHub HTTP integration
├── model/        # Profile statistics
├── config/       # Typed settings and HTTP client beans
└── exception/    # Application errors and global HTTP error handler
```

Only the Spring Boot entry point belongs directly in the root package. Tests mirror these package names. See [deployment options](docs/deployment.md) for hosting the Java backend and a separate frontend.

## License

[MIT](LICENSE), using the [Open Source Initiative license text](https://opensource.org/license/mit).

## Operational settings

| Setting | Default | Purpose |
| --- | --- | --- |
| `battle.timeout` | `30s` | Total comparison wait budget |
| `battle.cache-ttl` | `5m` | Successful profile lifetime |
| `battle.cache-size` | `1000` | Maximum cached profiles |
| `battle.upstream-workers` | `4` | Maximum concurrent GitHub profile loads |
| `battle.requests-per-minute` | `60` | Per-instance token bucket capacity and refill per minute |
| `FRONTEND_ORIGIN` | empty | Exact allowed browser origin |

`GET /actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness` expose status only. Probes do not call GitHub or spend quota. Other management endpoints remain unexposed. Health means the application is running, not that GitHub is available.

Logs report comparison status and duration plus upstream HTTP status and remaining/reset quota headers. They omit usernames, URLs, request bodies, and authorization headers. Actuator also records standard HTTP server metrics internally; metrics are not publicly exposed. Weekly Dependabot PRs cover Maven and GitHub Actions updates. A dependency-review workflow checks PR dependency changes for high/critical advisories; it is not a full runtime security audit.

Planning assumption: a read-only portfolio demo with peak demand around one request/second, confirmed by the owner. This is not a measured capacity or availability guarantee. No persistent user data is stored; cache loss only requires refetching profiles. Validate latency and recovery time on the selected host before assigning production SLOs.