package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.RideCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

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

        RideCategory category,

        @Valid
        @Size(max = 5, message = "Maximo de 5 paradas intermediarias.")
        List<StopPoint> stops
) {
    public CreateRideRequest(BigDecimal latOrigem, BigDecimal lngOrigem,
                             BigDecimal latDestino, BigDecimal lngDestino) {
        this(latOrigem, lngOrigem, latDestino, lngDestino, null, null);
    }

    public CreateRideRequest(BigDecimal latOrigem, BigDecimal lngOrigem,
                             BigDecimal latDestino, BigDecimal lngDestino, RideCategory category) {
        this(latOrigem, lngOrigem, latDestino, lngDestino, category, null);
    }
}
