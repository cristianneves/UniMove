package com.unimove.shared.security;

import java.util.UUID;

/**
 * Porta consultada pelo {@link JwtAuthenticationFilter} a cada request
 * autenticado. O JWT e stateless e vale ate expirar; sem esta checagem uma
 * conta suspensa (ou excluida) continuaria usando o token ja emitido.
 */
public interface AccountStatusChecker {

    AccountState stateOf(UUID userId);

    enum AccountState {
        ACTIVE,
        SUSPENDED,
        /** Conta excluida/anonimizada ou inexistente — o token nao representa mais ninguem. */
        GONE
    }
}
