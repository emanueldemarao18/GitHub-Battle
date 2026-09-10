package com.github.battle.app.controller;

import com.github.battle.app.model.Profile;
import com.github.battle.app.service.ProfileProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "github.token=")
class BattleApiIntegrationTests {
    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private ProfileProvider profiles;

    @Test
    void realHttpServerExposesBattleContractAndProblemDetails() throws Exception {
        when(profiles.fetch("left")).thenReturn(new Profile("left", null, "", "", 3, 3, 3, null, null, Instant.EPOCH));
        when(profiles.fetch("right")).thenReturn(new Profile("right", null, "", "", 1, 1, 1, null, null, Instant.EPOCH));
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/battles?left=left&right=right")).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        var body = new JsonMapper().readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.path("winner").asText()).isEqualTo("left");
        assertThat(body.path("leftScore").asDouble()).isEqualTo(75);
        assertThat(body.path("left").path("commits").isNull()).isTrue();
        assertThat(body.path("left").path("fetchedAt").asText()).isEqualTo("1970-01-01T00:00:00Z");

        var invalid = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/battles?left=left")).build();
        var error = client.send(invalid, HttpResponse.BodyHandlers.ofString());
        assertThat(error.statusCode()).isEqualTo(400);
        assertThat(error.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(new JsonMapper().readTree(error.body()).path("detail").asText()).contains("Both 'left' and 'right'");
    }
}
