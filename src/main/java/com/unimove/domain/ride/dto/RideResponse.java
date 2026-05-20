package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.CancelledBy;
import com.unimove.domain.ride.PaymentMethod;
import com.unimove.domain.ride.Ride;
import com.unimove.domain.ride.RideStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RideResponse(
        UUID id,
        UUID passageiroId,
        UUID motoristaId,
        String cidade,
        BigDecimal latOrigem,
        BigDecimal lngOrigem,
        BigDecimal latDestino,
        BigDecimal lngDestino,
        BigDecimal distanciaKm,
        Integer tempoMin,
        BigDecimal preco,
        RideStatus status,
        PaymentMethod paymentMethod,
        String pixPayload,
        BigDecimal driverCurrentLat,
        BigDecimal driverCurrentLng,
        Instant driverLocationUpdatedAt,
        BigDecimal driverDistanceKm,
        Instant createdAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        CancelledBy cancelledBy,
        String cancelReason
) {
    public static RideResponse from(Ride r) {
        return from(r, null);
    }

    public static RideResponse from(Ride r, BigDecimal driverDistanceKm) {
        return new RideResponse(
                r.getId(),
                r.getPassageiroId(),
                r.getMotoristaId(),
                r.getCidade(),
                r.getLatOrigem(),
                r.getLngOrigem(),
                r.getLatDestino(),
                r.getLngDestino(),
                r.getDistanciaKm(),
                r.getTempoMin(),
                r.getPreco(),
                r.getStatus(),
                r.getPaymentMethod(),
                r.getPixPayload(),
                r.getDriverCurrentLat(),
                r.getDriverCurrentLng(),
                r.getDriverLocationUpdatedAt(),
                driverDistanceKm,
                r.getCreatedAt(),
                r.getAcceptedAt(),
                r.getStartedAt(),
                r.getCompletedAt(),
                r.getCancelledAt(),
                r.getCancelledBy(),
                r.getCancelReason()
        );
    }
}
