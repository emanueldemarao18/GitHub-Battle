package com.github.battle.app.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GithubProperties.class)
public class GithubConfiguration {
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
        return builder.build();
    }
}
