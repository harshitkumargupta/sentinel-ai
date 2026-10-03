package com.sentinelai.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sentinelAiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SentinelAI API")
                        .description("AI-powered mini SOC platform — modular monolith API")
                        .version("0.1.0")
                        .license(new License().name("MIT")));
    }
}
