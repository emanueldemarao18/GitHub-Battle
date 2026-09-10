package com.github.battle.app.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("github")
public record GithubProperties(@DefaultValue("https://api.github.com") URI baseUrl,
                               @DefaultValue("") String token,
                               @DefaultValue("100") int maxRepositoryPages,
                               @DefaultValue("5s") Duration connectTimeout,
                               @DefaultValue("15s") Duration readTimeout) {
    public GithubProperties {
        if (baseUrl == null || baseUrl.getHost() == null ||
                !("https".equals(baseUrl.getScheme()) || "http".equals(baseUrl.getScheme())))
            throw new IllegalArgumentException("github.base-url must be an absolute HTTP(S) URL");
        if (maxRepositoryPages < 1 || maxRepositoryPages > 100)
            throw new IllegalArgumentException("github.max-repository-pages must be between 1 and 100");
        if (connectTimeout == null || connectTimeout.toMillis() < 1 || connectTimeout.toMillis() > 60000 ||
                readTimeout == null || readTimeout.toMillis() < 1 || readTimeout.toMillis() > 60000)
            throw new IllegalArgumentException("GitHub timeouts must be between 1ms and 60s");
        token = token == null ? "" : token.trim();
    }

    @Override
    public String toString() {
        return "GithubProperties[baseUrl=" + baseUrl + ", token=<redacted>, maxRepositoryPages=" + maxRepositoryPages + "]";
    }
}
