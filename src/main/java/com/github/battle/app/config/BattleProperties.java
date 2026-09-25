package com.github.battle.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("battle")
public record BattleProperties(@DefaultValue("30s") Duration timeout,
        @DefaultValue("5m") Duration cacheTtl, @DefaultValue("1000") int cacheSize,
        @DefaultValue("4") int upstreamWorkers, @DefaultValue("60") int requestsPerMinute,
        @DefaultValue("") String allowedOrigin) {
    public BattleProperties {
        if (timeout == null || timeout.toMillis() < 1 || timeout.compareTo(Duration.ofSeconds(60)) > 0)
            throw new IllegalArgumentException("battle.timeout must be between 1ms and 60s");
        if (cacheTtl == null || cacheTtl.toMillis() < 1 || cacheTtl.compareTo(Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("battle.cache-ttl must be positive and at most 24h");
        if (cacheSize < 1 || cacheSize > 10000 || upstreamWorkers < 1 || upstreamWorkers > 32 || requestsPerMinute < 1)
            throw new IllegalArgumentException("Invalid battle capacity settings");
        allowedOrigin = allowedOrigin == null ? "" : allowedOrigin.trim();
        if (!allowedOrigin.isEmpty()) {
            var origin = java.net.URI.create(allowedOrigin);
            if (!("https".equals(origin.getScheme()) || "http".equals(origin.getScheme())) || origin.getHost() == null
                    || origin.getRawUserInfo() != null || origin.getRawQuery() != null || origin.getRawFragment() != null
                    || !origin.getRawPath().isEmpty())
                throw new IllegalArgumentException("battle.allowed-origin must be an exact HTTP(S) origin without a path");
        }
    }
}
