package com.github.battle.app.config;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubPropertiesTests {
    @Test
    void rejectsUnboundedTimeoutsAndInvalidPageLimits() {
        for (int pages : new int[]{0, 101}) {
            assertThatThrownBy(() -> new GithubProperties(URI.create("https://api.github.com"), "", pages,
                    Duration.ofSeconds(1), Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new GithubProperties(URI.create("https://api.github.com"), "", 1,
                Duration.ZERO, Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverIncludesTokenInDiagnosticString() {
        var properties = new GithubProperties(URI.create("https://api.github.com"), "secret-token", 1,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertThat(properties.toString()).contains("<redacted>").doesNotContain("secret-token");
    }
}
