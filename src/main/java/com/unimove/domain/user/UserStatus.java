package com.unimove.domain.user;

public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    /** Excluida a pedido do titular (LGPD): linha anonimizada, nunca reativada. */
    DELETED
}
