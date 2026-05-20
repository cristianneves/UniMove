package com.unimove.domain.user.dto;

import com.unimove.domain.user.Driver;
import com.unimove.domain.user.VehicleType;

import java.time.Instant;
import java.util.UUID;

public record DriverStatusResponse(
        UUID userId,
        boolean online,
        boolean approved,
        Instant lastSeenAt,
        VehicleType vehicleType,
        String vehiclePlate
) {
    public static DriverStatusResponse from(Driver d) {
        return new DriverStatusResponse(
                d.getUserId(),
                d.isOnline(),
                d.isApproved(),
                d.getLastSeenAt(),
                d.getVehicleType(),
                d.getVehiclePlate()
        );
    }
}
