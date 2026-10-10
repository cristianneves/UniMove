package com.unimove.shared.config;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DestructiveMigrationGuardTest {

    @Test
    void onlyRunsBeforeMigrate() {
        DestructiveMigrationGuard guard = new DestructiveMigrationGuard(false);
        assertThat(guard.supports(Event.BEFORE_MIGRATE, null)).isTrue();
        assertThat(guard.supports(Event.AFTER_MIGRATE, null)).isFalse();
        assertThat(guard.supports(Event.BEFORE_EACH_MIGRATE, null)).isFalse();
    }

    @Test
    void explicitOverrideSkipsTheCheck() {
        assertThatCode(() -> new DestructiveMigrationGuard(true).verify(null)).doesNotThrowAnyException();
    }

    /** SQL real do guard, num schema descartavel do Postgres de IT. */
    @Nested
    @Tag("integration")
    @EnabledIfEnvironmentVariable(named = "IT_DATABASE_URL", matches = ".+")
    class AgainstPostgres {

        private Connection connection;
        private String schema;

        @BeforeEach
        void openScratchSchema() throws SQLException {
            connection = DriverManager.getConnection(System.getenv("IT_DATABASE_URL"),
                    env("IT_DATABASE_USER", "postgres"), env("IT_DATABASE_PASSWORD", "it"));
            schema = "guard_" + UUID.randomUUID().toString().substring(0, 8);
            exec("CREATE SCHEMA " + schema, "SET search_path TO " + schema);
        }

        @AfterEach
        void dropScratchSchema() throws SQLException {
            exec("DROP SCHEMA " + schema + " CASCADE");
            connection.close();
        }

        @Test
        void emptyDatabasePasses() {
            assertThatCode(() -> new DestructiveMigrationGuard(false).verify(connection)).doesNotThrowAnyException();
        }

        @Test
        void belowV19WithOnlyAdminPasses() throws SQLException {
            schemaAt("18", "ADMIN");
            assertThatCode(() -> new DestructiveMigrationGuard(false).verify(connection)).doesNotThrowAnyException();
        }

        @Test
        void belowV19WithRealUsersIsBlocked() throws SQLException {
            schemaAt("18", "ADMIN", "PASSAGEIRO", "MOTORISTA");
            assertThatThrownBy(() -> new DestructiveMigrationGuard(false).verify(connection))
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("2 usuario(s)")
                    .hasMessageContaining("UNIMOVE_ALLOW_DESTRUCTIVE_V19");
        }

        @Test
        void belowV19WithRealUsersPassesWhenExplicitlyAllowed() throws SQLException {
            schemaAt("18", "PASSAGEIRO");
            assertThatCode(() -> new DestructiveMigrationGuard(true).verify(connection)).doesNotThrowAnyException();
        }

        @Test
        void alreadyPastV19Passes() throws SQLException {
            schemaAt("19", "PASSAGEIRO");
            assertThatCode(() -> new DestructiveMigrationGuard(false).verify(connection)).doesNotThrowAnyException();
        }

        @Test
        void failedV19DoesNotCountAsApplied() throws SQLException {
            schemaAt("18", "PASSAGEIRO");
            exec("INSERT INTO flyway_schema_history VALUES ('19', false)");
            assertThatThrownBy(() -> new DestructiveMigrationGuard(false).verify(connection))
                    .isInstanceOf(FlywayException.class);
        }

        /** Historico ate {@code version} e um usuario por role informada. */
        private void schemaAt(String version, String... roles) throws SQLException {
            exec("CREATE TABLE flyway_schema_history (version VARCHAR(50), success BOOLEAN NOT NULL)",
                    "CREATE TABLE users (role VARCHAR(20) NOT NULL)");
            for (int v = 1; v <= Integer.parseInt(version); v++) {
                exec("INSERT INTO flyway_schema_history VALUES ('" + v + "', true)");
            }
            for (String role : roles) {
                exec("INSERT INTO users VALUES ('" + role + "')");
            }
        }

        private void exec(String... sql) throws SQLException {
            try (Statement st = connection.createStatement()) {
                for (String s : sql) {
                    st.execute(s);
                }
            }
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
