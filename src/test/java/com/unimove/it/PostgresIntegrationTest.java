package com.unimove.it;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Teste de integracao contra Postgres real (CLAUDE.md: nada de H2). O banco
 * vem de {@code IT_DATABASE_URL}; sem a variavel o teste e pulado, entao
 * {@code ./mvnw verify} continua passando numa maquina sem Postgres.
 * Testcontainers nao foi usado porque nao roda no Docker Desktop do time.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "IT_DATABASE_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public @interface PostgresIntegrationTest {
}
