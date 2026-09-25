package com.github.battle.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class BattleWebConfiguration implements WebMvcConfigurer {
    private final BattleProperties properties;
    private final BattleTrafficInterceptor traffic;
    public BattleWebConfiguration(BattleProperties properties, BattleTrafficInterceptor traffic) {
        this.properties = properties;
        this.traffic = traffic;
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traffic).addPathPatterns("/api/battles");
    }
    @Override public void addCorsMappings(CorsRegistry registry) {
        if (!properties.allowedOrigin().isEmpty()) {
            registry.addMapping("/api/battles").allowedOrigins(properties.allowedOrigin())
                    .allowedMethods("GET").allowedHeaders("Accept", "Content-Type")
                    .exposedHeaders("Retry-After").allowCredentials(false).maxAge(3600);
        }
    }
}
