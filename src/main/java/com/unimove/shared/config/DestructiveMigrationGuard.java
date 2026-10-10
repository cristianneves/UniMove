package com.unimove.shared.config;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Trava a V19 em banco com dados reais. A V19 apaga todas as corridas e todos
 * os usuarios nao-ADMIN; ela so roda uma vez por banco, entao o risco e um
 * banco ainda abaixo dela (ex.: restore antigo) receber a API nova. Nesse caso
 * o boot para antes de qualquer migration, a nao ser que o operador libere
 * explicitamente com {@code UNIMOVE_ALLOW_DESTRUCTIVE_V19=true}.
 *
 * <p>Banco novo passa direto: antes da V1 nao existe a tabela {@code users}.
 */
@Component
public class DestructiveMigrationGuard implements Callback {

    private final boolean allowDestructiveV19;

    public DestructiveMigrationGuard(@Value("${app.flyway.allow-destructive-v19:false}") boolean allowDestructiveV19) {
        this.allowDestructiveV19 = allowDestructiveV19;
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        try {
            verify(context.getConnection());
        } catch (SQLException e) {
            throw new FlywayException("Falha ao checar a V19 antes do migrate", e);
        }
    }

    @Override
    public String getCallbackName() {
        return "destructive-v19-guard";
    }

    void verify(Connection connection) throws SQLException {
        if (allowDestructiveV19 || !exists(connection, "users") || v19Applied(connection)) {
            return;
        }
        long realUsers = count(connection, "SELECT count(*) FROM users WHERE role <> 'ADMIN'");
        if (realUsers > 0) {
            throw new FlywayException("Banco abaixo da V19 com " + realUsers + " usuario(s) nao-ADMIN. "
                    + "A V19 apaga todos eles e todas as corridas. Faca backup e, se for mesmo o caso, "
                    + "suba com UNIMOVE_ALLOW_DESTRUCTIVE_V19=true (docs/homologacao-runbook.md).");
        }
    }

    private static boolean v19Applied(Connection connection) throws SQLException {
        return exists(connection, "flyway_schema_history")
                && count(connection, "SELECT count(*) FROM flyway_schema_history WHERE version = '19' AND success") > 0;
    }

    private static boolean exists(Connection connection, String table) throws SQLException {
        return count(connection, "SELECT count(*) FROM pg_tables WHERE schemaname = current_schema() "
                + "AND tablename = '" + table + "'") > 0;
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
