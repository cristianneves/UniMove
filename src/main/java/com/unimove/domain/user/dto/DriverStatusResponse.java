package com.unimove.domain.user.dto;

import com.unimove.domain.user.Driver;
import com.unimove.domain.user.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DriverStatusResponse(
        UUID userId,
        boolean online,
        boolean approved,
        Instant lastSeenAt,
        VehicleType vehicleType,
        String vehiclePlate,
        BigDecimal ratingAvg,
        Integer ratingCount
) {
    public static DriverStatusResponse from(Driver d) {
        return new DriverStatusResponse(
                d.getUserId(),
                d.isOnline(),
                d.isApproved(),
                d.getLastSeenAt(),
                d.getVehicleType(),
                d.getVehiclePlate(),
                d.getUser() != null ? d.getUser().getRatingAvg() : BigDecimal.ZERO,
                d.getUser() != null ? d.getUser().getRatingCount() : 0
        );
    }
}
