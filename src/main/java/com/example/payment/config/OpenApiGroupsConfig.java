package com.example.payment.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiGroupsConfig {

    @Bean
    public GroupedOpenApi demoApi() {
        return GroupedOpenApi.builder()
                .group("Demo API")
                .pathsToMatch("/demo/api/**")
                .build();
    }

    @Bean
    public GroupedOpenApi productionApi() {
        return GroupedOpenApi.builder()
                .group("Production API")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/auth/**", "/api/webhooks/**")
                .build();
    }

    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("Auth API")
                .pathsToMatch("/api/auth/**")
                .build();
    }
}
