package com.unimove.domain.user.dto;

/**
 * Contagens de usuários para o painel admin (regra de fronteira: o domain.ride
 * consome isto via {@code UserStatsService}, sem importar entidades do user).
 */
public record UserStatsSnapshot(
        long totalPassengers,
        long totalDrivers,
        long onlineDrivers,
        long pendingDrivers,
        long suspendedUsers
) {}
