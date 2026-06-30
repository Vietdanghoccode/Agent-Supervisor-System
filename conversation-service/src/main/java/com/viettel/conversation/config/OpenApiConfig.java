package com.viettel.conversation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI conversationOpenApi(
            @Value("${app.openapi.server-url:http://localhost:8080}") String serverUrl) {
        return new OpenAPI().servers(List.of(new Server().url(serverUrl)));
    }
}
