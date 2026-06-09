package com.unimove.domain.user.dto;

import com.unimove.domain.user.Driver;
import com.unimove.domain.user.Role;
import com.unimove.domain.user.User;
import com.unimove.domain.user.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Perfil completo do usuário autenticado. {@code vehicle} só vem preenchido
 * para MOTORISTA.
 */
public record UserProfileResponse(
        UUID userId,
        String email,
        String name,
        String phone,
        Role role,
        String cidade,
        BigDecimal ratingAvg,
        Integer ratingCount,
        Instant createdAt,
        DriverVehicleInfo vehicle
) {
    public record DriverVehicleInfo(
            VehicleType vehicleType,
            String vehiclePlate,
            boolean approved,
            boolean online
    ) {
    }

    public static UserProfileResponse from(User user, Driver driver) {
        DriverVehicleInfo vehicle = driver == null ? null : new DriverVehicleInfo(
                driver.getVehicleType(),
                driver.getVehiclePlate(),
                driver.isApproved(),
                driver.isOnline()
        );
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getRole(),
                user.getCidade(),
                user.getRatingAvg(),
                user.getRatingCount(),
                user.getCreatedAt(),
                vehicle
        );
    }
}
