package com.cocoscap.challenge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata (ADR-001). Snake_case schemas via Jackson 2 {@link ModelResolver} because
 * springdoc does not pick up Jackson 3 {@code SNAKE_CASE} alone (ADR-010).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI cocosChallengeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cocos Challenge API")
                        .version("1.0")
                        .description(
                                "Portfolio, instrument search, and order placement/cancellation. "
                                        + "See the README and docs/ for behaviour, ADRs, and assumptions."))
                .components(new Components()
                        .addSchemas(
                                "ProblemDetail",
                                new ObjectSchema()
                                        .addProperty("type", new StringSchema())
                                        .addProperty("title", new StringSchema())
                                        .addProperty("status", new IntegerSchema())
                                        .addProperty("detail", new StringSchema())
                                        .addProperty("instance", new StringSchema())
                                        .addProperty("code", new StringSchema().description("ADR-007 catalog"))
                                        .addProperty("errors", new ObjectSchema())
                                        .addProperty("current_status", new StringSchema())));
    }

    @Bean
    ModelResolver snakeCaseModelResolver() {
        ObjectMapper mapper = Json.mapper().copy();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new ModelResolver(mapper);
    }
}
