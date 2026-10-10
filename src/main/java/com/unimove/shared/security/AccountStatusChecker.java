package com.unimove.shared.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Porta consultada pelo {@link JwtAuthenticationFilter} a cada request
 * autenticado. O JWT e stateless e vale ate expirar; sem esta checagem uma
 * conta suspensa (ou excluida, ou com a senha trocada) continuaria usando o
 * token ja emitido.
 */
public interface AccountStatusChecker {

    /**
     * @param tokenIssuedAt {@code iat} do JWT — token anterior a uma troca de
     *                      senha e considerado revogado.
     */
    AccountState stateOf(UUID userId, Instant tokenIssuedAt);

    enum AccountState {
        ACTIVE,
        /** Senha temporaria: so a troca de senha (e a leitura do perfil) e liberada. */
        PASSWORD_CHANGE_REQUIRED,
        SUSPENDED,
        /** Token emitido antes da ultima troca/reset de senha. */
        REVOKED,
        /** Conta excluida/anonimizada ou inexistente — o token nao representa mais ninguem. */
        GONE
    }
}
