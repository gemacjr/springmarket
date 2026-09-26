package com.springmarket.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiConfigTest {
    @Test
    void appliesBearerJwtSecurityRequirement() {
        OpenAPI openAPI = new OpenApiConfig().openAPI();

        assertEquals(
                java.util.List.of("bearer-jwt"),
                openAPI.getSecurity().getFirst().keySet().stream().toList());
    }
}
