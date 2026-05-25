package com.unimove.domain.ride;

public enum RideStatus {
    PENDING_PAYMENT,
    AVAILABLE_IN_MURAL,
    DRIVER_EN_ROUTE,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    /** Nenhum motorista aceitou a corrida no mural dentro do TTL — expirada pelo sistema. */
    EXPIRED
}
