package com.unimove.domain.user.dto;

import com.unimove.domain.user.VehicleType;

import java.time.Instant;
import java.util.UUID;

public record PendingDriverItem(
        UUID userId,
        String name,
        String email,
        String phone,
        String cidade,
        VehicleType vehicleType,
        String vehiclePlate,
        Instant registeredAt
) {}
