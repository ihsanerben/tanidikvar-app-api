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
                .description("Üniversite deneyimlerini buluşturan platform. İlk teslim: uygulama temeli."));
    }
}
