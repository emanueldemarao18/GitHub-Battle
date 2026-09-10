package com.github.battle.app.client;

import com.github.battle.app.config.GithubConfiguration;
import com.github.battle.app.config.GithubProperties;
import com.github.battle.app.exception.BattleException;
import java.net.URI;
import java.time.Duration;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class GithubClientTests {
    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private String authorization;
    private int status = 200;
    private boolean paginate;
    private boolean malformed;
    private boolean graphqlError;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String body = """
                {"login":"octocat","name":null,"type":"User","avatar_url":"https://avatars.githubusercontent.com/u/1",
                 "html_url":"https://github.com/octocat","followers":10,"public_repos":2}
                """;
            if (exchange.getRequestURI().getPath().endsWith("/repos")) {
                body = "[{\"stargazers_count\":7}]";
                if (paginate && exchange.getRequestURI().getQuery().endsWith("page=1"))
                    exchange.getResponseHeaders().add("Link", "<http://example.invalid/page2>; rel=\"next\"");
            }
            if (exchange.getRequestURI().getPath().equals("/graphql")) {
                body = graphqlError ? "{\"errors\":[{\"type\":\"RATE_LIMITED\"}]}" : """
                    {"data":{"user":{"contributionsCollection":{"totalCommitContributions":42,
                      "contributionCalendar":{"weeks":[{"contributionDays":[
                        {"date":"2026-01-01","contributionCount":1},
                        {"date":"2026-01-02","contributionCount":2}]}]}}}}}
                    """;
            }
            if (malformed) body = "{}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private GithubClient client(String token, int pages) {
        var properties = new GithubProperties(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                token, pages, Duration.ofSeconds(1), Duration.ofSeconds(1));
        return new GithubClient(new GithubConfiguration().githubRestClient(properties), properties);
    }

    @Test
    void followsPaginationWithoutFollowingUntrustedLinkHosts() {
        paginate = true;
        var profile = client("", 10).fetch("octocat");
        assertThat(profile.stars()).isEqualTo(14);
        assertThat(profile.commits()).isNull();
        assertThat(profile.name()).isNull();
        assertThat(requests).hasSize(3);
        assertThat(authorization).isNull();
    }

    @Test
    void neverReturnsTruncatedRepositoryScores() {
        paginate = true;
        assertThatThrownBy(() -> client("", 1).fetch("octocat"))
                .isInstanceOfSatisfying(BattleException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void authenticatedRequestIncludesContributionMetrics() {
        var profile = client("test-token", 10).fetch("octocat");
        assertThat(profile.commits()).isEqualTo(42);
        assertThat(profile.longestStreak()).isEqualTo(2);
        assertThat(authorization).isEqualTo("Bearer test-token");
    }

    @Test
    void graphqlRateLimitsAreNotScoredAsZero() {
        graphqlError = true;
        assertThatThrownBy(() -> client("test-token", 10).fetch("octocat"))
                .isInstanceOfSatisfying(BattleException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void translatesUpstreamErrorsWithoutLeakingResponseBody() {
        for (int code : new int[]{401, 403, 404, 429, 500}) {
            status = code;
            HttpStatus expected = code == 404 ? HttpStatus.NOT_FOUND :
                    code == 403 || code == 429 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            assertThatThrownBy(() -> client("", 10).fetch("octocat"))
                    .isInstanceOfSatisfying(BattleException.class, e -> assertThat(e.status()).isEqualTo(expected));
        }
    }

    @Test
    void rejectsIncompletePayload() {
        malformed = true;
        assertThatThrownBy(() -> client("", 10).fetch("octocat")).isInstanceOf(BattleException.class);
    }

    @Test
    void streakResetsAtInactiveDaysAndDateGapsAcrossWeeks() {
        var weeks = new JsonMapper().readTree("""
            [{"contributionDays":[{"date":"2026-01-01","contributionCount":1}]},
             {"contributionDays":[{"date":"2026-01-02","contributionCount":1},
              {"date":"2026-01-03","contributionCount":0},
              {"date":"2026-01-04","contributionCount":1},
              {"date":"2026-01-06","contributionCount":1}]}]
            """);
        assertThat(GithubClient.longestStreak(weeks)).isEqualTo(2);
    }
}
