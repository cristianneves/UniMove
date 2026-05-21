package com.unimove.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI unimoveOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("UniMove API")
                        .version("0.1.0")
                        .description("""
                                Backend do UniMove — app de mobilidade urbana para cidades de pequeno porte.

                                Endpoints protegidos exigem `Authorization: Bearer <jwt>`. \
                                Obtenha o token via `POST /auth/login`.
                                """)
                        .contact(new Contact().name("UniMove").email("contato@unimove.local")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT emitido por /auth/login ou /auth/register")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
