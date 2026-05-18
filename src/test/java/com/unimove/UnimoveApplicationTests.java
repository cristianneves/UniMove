package com.unimove;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test: garante que o Spring context sobe e que o Flyway aplica
 * todas as migrations contra um Postgres real (via Testcontainers).
 *
 * Se este teste passar, o pom.xml, application.yml e V1__init_schema.sql
 * estao consistentes entre si.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class UnimoveApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // Vazio de proposito — basta o context subir sem erro.
    }
}
