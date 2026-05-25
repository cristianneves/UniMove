package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.RideCategory;
import com.unimove.domain.ride.RideStatus;
import com.unimove.domain.user.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Payload exposto publicamente via /share/{token}.
 * Intencionalmente NAO inclui: id da ride, ids de usuario, telefone,
 * email, preco, payload pix. Apenas o necessario para um terceiro
 * acompanhar a viagem.
 */
public record SharedRideResponse(
        RideStatus status,
        String cidade,
        RideCategory category,

        BigDecimal latOrigem,
        BigDecimal lngOrigem,
        BigDecimal latDestino,
        BigDecimal lngDestino,
        List<StopPoint> stops,

        String passengerFirstName,

        String driverFirstName,
        String driverVehiclePlate,
        VehicleType driverVehicleType,
        BigDecimal driverRatingAvg,
        Integer driverRatingCount,

        BigDecimal driverCurrentLat,
        BigDecimal driverCurrentLng,
        Instant driverLocationUpdatedAt,

        // Distancia em linha reta (km) do motorista ate o proximo ponto:
        // origem se DRIVER_EN_ROUTE, destino se IN_PROGRESS.
        BigDecimal driverDistanceKm,

        Instant createdAt,
        Instant acceptedAt,
        Instant startedAt
) {}
