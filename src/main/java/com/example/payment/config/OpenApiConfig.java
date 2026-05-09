package com.example.payment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String API_KEY_SECURITY_SCHEME = "ApiKeyAuth";
    public static final String BEARER_SECURITY_SCHEME = "BearerAuth";

    @Bean
    public OpenAPI paymentIntegrationOpenAPI() {
        return new OpenAPI()
                .servers(List.of(
                        new Server()
                                .url("https://payment-integration-service-production-18ae.up.railway.app")
                                .description("Railway Production Server"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server")
                ))
                .info(new Info()
                        .title("Payment Integration Service API")
                        .version("1.0.0")
                        .description("Spring Boot payment integration API with Stripe PaymentIntent, refund, webhook, idempotency, and PostgreSQL persistence workflows.")
                        .contact(new Contact()
                                .name("John Camp"))
                        .license(new License()
                                .name("Portfolio Demo")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(API_KEY_SECURITY_SCHEME)
                        .addList(BEARER_SECURITY_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(API_KEY_SECURITY_SCHEME,
                                new SecurityScheme()
                                        .name("X-API-Key")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("API key authentication for server-to-server requests."))
                        .addSecuritySchemes(BEARER_SECURITY_SCHEME,
                                new SecurityScheme()
                                        .name("Authorization")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT authentication. Paste only the token value; Swagger will add 'Bearer ' automatically.")));
    }
}
