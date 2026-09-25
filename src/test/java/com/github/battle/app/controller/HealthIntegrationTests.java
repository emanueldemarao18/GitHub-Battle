package com.github.battle.app.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "github.token=")
class HealthIntegrationTests {
    @Value("${local.server.port}") private int port;

    @Test void exposesHealthProbesWithoutDetailsOrOtherManagementEndpoints() throws Exception {
        var client = HttpClient.newHttpClient();
        for (String path : new String[]{"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var body = new JsonMapper().readTree(response.body());
            assertThat(body.path("status").asText()).isEqualTo("UP");
            assertThat(body.has("components")).isFalse();
            assertThat(body.has("details")).isFalse();
        }
        for (String path : new String[]{"/actuator/env", "/actuator/metrics", "/actuator/configprops"}) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(404);
        }
    }
}
