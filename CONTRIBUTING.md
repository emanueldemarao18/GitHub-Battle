# Contributing

GitHub Battle is a backend project. The frontend will live separately and consume the documented REST API.

## Local workflow

1. Fork the repository and create a focused branch from `main`.
2. Install JDK 17 or newer and set `JAVA_HOME`.
3. Run `./mvnw verify` (`./mvnw.cmd verify` on Windows). Tests do not need a GitHub token or external services.
4. Run `./mvnw spring-boot:run` to try the API on port 8081.
5. Make a focused change, add regression coverage for changed behavior, update documentation, and rerun verification.
6. Open a pull request explaining the problem, resulting behavior, and validation performed.

Discuss substantial features or breaking API changes in an issue before implementation. Bug reports should include a minimal request, expected behavior, actual response, Java version, and reproduction steps. Remove tokens and private information from logs.

## Package boundaries

All packages are under `com.github.battle.app`:

| Package | Responsibility |
| --- | --- |
| `controller` | HTTP routes, request parameters, and delegation to services |
| `dto` | Stable JSON response contracts |
| `service` | Username validation, comparison orchestration, scoring, and the `ProfileProvider` boundary |
| `client` | GitHub REST/GraphQL calls, pagination, response validation, and contribution parsing |
| `model` | Immutable profile statistics |
| `config` | Typed configuration and HTTP client construction |
| `exception` | Explicit application failures and global problem-detail handling |

Tests mirror production packages. Keep HTTP calls out of controllers and scoring. Depend on `ProfileProvider` when retrieving profiles; do not inject the concrete GitHub client into the service. Keep upstream JSON inside the client package. Use records for immutable data and constructor injection for dependencies. The application entry point stays at the root so Spring scans every child package. Do not create empty packages for unused layers (such as repositories when no database exists).

## Style and verification

- Use four-space Java indentation and descriptive names. Avoid wildcard production imports.
- Do not add dependencies, infrastructure, or abstraction layers without a concrete need.
- Preserve nullable metrics: unavailable data must not become zero or silently influence the winner.
- API changes must update `docs/openapi.yaml` and the README. Explain breaking changes in the PR.
- Scoring changes must update the documented formula and regression tests.
- Integration tests must use local fixtures. Do not spend real GitHub quota in CI.
- Never commit `.env`, tokens, generated build output, or editor settings.
- Do not expose upstream response bodies, authorization headers, or exception traces through the API.

This project uses the [MIT License](LICENSE). Contributions are accepted under that license.
