package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.RideCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateRideRequest(
        @NotNull
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        BigDecimal latOrigem,

        @NotNull
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        BigDecimal lngOrigem,

        @NotNull
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        BigDecimal latDestino,

        @NotNull
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        BigDecimal lngDestino,

        RideCategory category
) {
    public CreateRideRequest(BigDecimal latOrigem, BigDecimal lngOrigem,
                             BigDecimal latDestino, BigDecimal lngDestino) {
        this(latOrigem, lngOrigem, latDestino, lngDestino, null);
    }
}
