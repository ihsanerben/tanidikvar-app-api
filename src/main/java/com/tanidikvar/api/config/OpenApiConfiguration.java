package com.tanidikvar.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("TanıdıkVar API").version("0.1.0")
                .description("Üniversite deneyimlerini buluşturan platform. Web cookie mutasyonları CSRF gerektirir. /api/auth/mobile JSON uçları cookie üretmez; mobil korumalı uçlar Bearer JWT kullanır."))
                .components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("accessCookie",
                        new io.swagger.v3.oas.models.security.SecurityScheme().type(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY)
                                .in(io.swagger.v3.oas.models.security.SecurityScheme.In.COOKIE).name("TV_ACCESS"))
                        .addSecuritySchemes("bearerAuth", new io.swagger.v3.oas.models.security.SecurityScheme()
                                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
