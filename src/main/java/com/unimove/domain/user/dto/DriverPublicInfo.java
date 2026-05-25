package com.unimove.domain.user.dto;

import com.unimove.domain.user.VehicleType;

import java.math.BigDecimal;

public record DriverPublicInfo(
        String firstName,
        VehicleType vehicleType,
        String vehiclePlate,
        BigDecimal ratingAvg,
        Integer ratingCount
) {}
