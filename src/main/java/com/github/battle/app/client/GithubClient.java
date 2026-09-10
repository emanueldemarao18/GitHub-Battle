package com.github.battle.app.client;

import com.github.battle.app.config.GithubProperties;
import com.github.battle.app.exception.BattleException;
import com.github.battle.app.model.Profile;
import com.github.battle.app.service.ProfileProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class GithubClient implements ProfileProvider {
    private final RestClient client;
    private final boolean authenticated;
    private final int maxPages;
    private static final String CONTRIBUTIONS = """
        query($login: String!) {
          user(login: $login) {
            contributionsCollection {
              totalCommitContributions
              contributionCalendar { weeks { contributionDays { date contributionCount } } }
            }
          }
        }
        """;

    public GithubClient(RestClient githubRestClient, GithubProperties properties) {
        this.maxPages = properties.maxRepositoryPages();
        this.authenticated = !properties.token().isBlank();
        this.client = githubRestClient;
    }

    @Override
    public Profile fetch(String username) {
        try {
            JsonNode user = get("/users/" + username);
            if (!user.path("type").isString() || !user.path("login").isString()) throw malformed();
            if (!"User".equals(user.path("type").asText())) {
                throw new BattleException(HttpStatus.BAD_REQUEST, "Choose personal GitHub accounts, not organizations.");
            }
            long stars = 0;
            boolean complete = false;
            for (int page = 1; page <= maxPages; page++) {
                var response = client.get().uri("/users/{username}/repos?type=owner&per_page=100&page={page}", username, page)
                        .retrieve().toEntity(JsonNode.class);
                JsonNode repositories = response.getBody();
                if (repositories == null || !repositories.isArray()) throw malformed();
                for (JsonNode repository : repositories) stars += number(repository, "stargazers_count");
                String link = response.getHeaders().getFirst("Link");
                if (link == null || !link.contains("rel=\"next\"")) {
                    complete = true;
                    break;
                }
            }
            if (!complete) throw new BattleException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This account exceeds the repository lookup limit; a partial score would be misleading.");
            Long commits = null;
            Long streak = null;
            if (authenticated) {
                JsonNode result = client.post().uri("/graphql").body(Map.of("query", CONTRIBUTIONS,
                        "variables", Map.of("login", username))).retrieve().body(JsonNode.class);
                if (result == null) throw malformed();
                if (result.has("errors")) {
                    for (JsonNode error : result.path("errors")) {
                        if ("RATE_LIMITED".equals(error.path("type").asText()))
                            throw new BattleException(HttpStatus.SERVICE_UNAVAILABLE, "GitHub rate limit reached. Please try again later.");
                    }
                    throw new BattleException(HttpStatus.BAD_GATEWAY, "GitHub could not provide contribution data. Check the server token permissions.");
                }
                JsonNode collection = result.path("data").path("user").path("contributionsCollection");
                commits = number(collection, "totalCommitContributions");
                streak = longestStreak(collection.path("contributionCalendar").path("weeks"));
            }
            return new Profile(user.path("login").asText(), user.path("name").asString(null),
                    user.path("avatar_url").asText(), user.path("html_url").asText(),
                    number(user, "followers"), number(user, "public_repos"), stars, commits, streak, Instant.now());
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404) throw new BattleException(HttpStatus.NOT_FOUND, "GitHub user '" + username + "' was not found.");
            if (status == 429 || status == 403) throw new BattleException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GitHub denied the request or its rate limit was reached. Please try again later.");
            if (status == 401) throw new BattleException(HttpStatus.BAD_GATEWAY, "The server's GitHub token is invalid.");
            throw new BattleException(HttpStatus.BAD_GATEWAY, "GitHub is temporarily unavailable.");
        } catch (RestClientException ex) {
            throw new BattleException(HttpStatus.BAD_GATEWAY, "Could not retrieve data from GitHub. Please try again later.");
        }
    }

    private JsonNode get(String path) {
        JsonNode result = client.get().uri(path).retrieve().body(JsonNode.class);
        if (result == null || !result.isObject()) throw malformed();
        return result;
    }

    private static long number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || value.asLong() < 0) throw malformed();
        return value.asLong();
    }

    static long longestStreak(JsonNode weeks) {
        if (!weeks.isArray()) throw malformed();
        long best = 0, current = 0;
        LocalDate previous = null;
        for (JsonNode week : weeks) {
            if (!week.path("contributionDays").isArray()) throw malformed();
            for (JsonNode day : week.path("contributionDays")) {
                LocalDate date;
                try { date = LocalDate.parse(day.path("date").asText()); }
                catch (java.time.format.DateTimeParseException ex) { throw malformed(); }
                if (number(day, "contributionCount") > 0) {
                    current = previous != null && previous.plusDays(1).equals(date) ? current + 1 : 1;
                    best = Math.max(best, current);
                } else current = 0;
                previous = date;
            }
        }
        return best;
    }

    private static BattleException malformed() {
        return new BattleException(HttpStatus.BAD_GATEWAY, "GitHub returned incomplete or invalid data.");
    }
}
