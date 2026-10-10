package com.unimove.domain.ride;

public enum CancelledBy {
    PASSAGEIRO,
    MOTORISTA,
    /** Cancelamento administrativo (corrida presa, incidente) — V30. */
    ADMIN
}
