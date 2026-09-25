package com.github.battle.app.config;

import com.github.battle.app.service.ComparisonDeadline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({GithubProperties.class, BattleProperties.class})
public class GithubConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(GithubConfiguration.class);
    @Bean
    public RestClient githubRestClient(GithubProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        var builder = RestClient.builder().baseUrl(properties.baseUrl().toString()).requestFactory(factory)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2026-03-10")
                .defaultHeader("User-Agent", "github-battle");
        if (!properties.token().isBlank()) builder.defaultHeader("Authorization", "Bearer " + properties.token());
        builder.requestInterceptor((request, body, execution) -> {
            ComparisonDeadline.remainingNanos();
            var response = execution.execute(request, body);
            LOG.info(
                    "github_response status={} quota_remaining={} quota_reset={}", response.getStatusCode().value(),
                    response.getHeaders().getFirst("X-RateLimit-Remaining"), response.getHeaders().getFirst("X-RateLimit-Reset"));
            return response;
        });
        return builder.build();
    }
}
