package com.unimove.domain.ride.dto;

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
        Instant createdAt
) {
    public static RideResponse from(Ride r) {
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
                r.getCreatedAt()
        );
    }
}
