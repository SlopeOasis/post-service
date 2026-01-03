package com.slopeoasis.post.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {

        ApiResponse unauthorized = new ApiResponse()
            .description("Missing or invalid Authorization header")
            .content(new Content().addMediaType(
                "application/json",
                new MediaType().schema(
                    new Schema<>()
                        .type("object")
                        .addProperty("error",
                            new Schema<>().type("string")
                                .example("Missing or invalid Authorization header")
                        )
                )
            ));

        return new OpenAPI()
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                )
                .addResponses("UnauthorizedError", unauthorized)
            );
    }
}
