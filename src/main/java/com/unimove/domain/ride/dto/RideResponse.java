package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.CancelledBy;
import com.unimove.domain.ride.PaymentMethod;
import com.unimove.domain.ride.Ride;
import com.unimove.domain.ride.RideCategory;
import com.unimove.domain.ride.RideStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
        List<StopPoint> stops,
        BigDecimal distanciaKm,
        Integer tempoMin,
        BigDecimal preco,
        BigDecimal surgeMultiplier,
        RideCategory category,
        RideStatus status,
        PaymentMethod paymentMethod,
        String pixPayload,
        BigDecimal driverCurrentLat,
        BigDecimal driverCurrentLng,
        Instant driverLocationUpdatedAt,
        BigDecimal driverDistanceKm,
        Integer pickupEtaMin,
        BigDecimal motoristaRatingAvg,
        Integer motoristaRatingCount,
        Instant createdAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant completedAt,
        Instant expiredAt,
        Instant cancelledAt,
        CancelledBy cancelledBy,
        String cancelReason,
        BigDecimal cancellationFee
) {
    public static RideResponse from(Ride r) {
        return from(r, null, null, null);
    }

    public static RideResponse from(Ride r, BigDecimal driverDistanceKm) {
        return from(r, driverDistanceKm, null, null);
    }

    public static RideResponse from(Ride r,
                                    BigDecimal driverDistanceKm,
                                    BigDecimal motoristaRatingAvg,
                                    Integer motoristaRatingCount) {
        List<StopPoint> stops = r.getStops().stream()
                .map(s -> new StopPoint(s.getLat(), s.getLng()))
                .toList();
        return new RideResponse(
                r.getId(),
                r.getPassageiroId(),
                r.getMotoristaId(),
                r.getCidade(),
                r.getLatOrigem(),
                r.getLngOrigem(),
                r.getLatDestino(),
                r.getLngDestino(),
                stops,
                r.getDistanciaKm(),
                r.getTempoMin(),
                r.getPreco(),
                r.getSurgeMultiplier(),
                r.getCategory(),
                r.getStatus(),
                r.getPaymentMethod(),
                r.getPixPayload(),
                r.getDriverCurrentLat(),
                r.getDriverCurrentLng(),
                r.getDriverLocationUpdatedAt(),
                driverDistanceKm,
                r.getPickupEtaMin(),
                motoristaRatingAvg,
                motoristaRatingCount,
                r.getCreatedAt(),
                r.getAcceptedAt(),
                r.getStartedAt(),
                r.getCompletedAt(),
                r.getExpiredAt(),
                r.getCancelledAt(),
                r.getCancelledBy(),
                r.getCancelReason(),
                r.getCancellationFee()
        );
    }
}
