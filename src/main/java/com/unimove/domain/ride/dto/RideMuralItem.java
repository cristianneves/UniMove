package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.PaymentMethod;
import com.unimove.domain.ride.Ride;
import com.unimove.domain.ride.RideCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RideMuralItem(
        UUID id,
        BigDecimal latOrigem,
        BigDecimal lngOrigem,
        BigDecimal latDestino,
        BigDecimal lngDestino,
        BigDecimal distanciaKm,
        Integer tempoMin,
        BigDecimal preco,
        RideCategory category,
        PaymentMethod paymentMethod,
        Instant createdAt
) {

    /** Mesmo shape do item da query do mural — usado no evento SSE "ride-added". */
    public static RideMuralItem from(Ride ride) {
        return new RideMuralItem(
                ride.getId(),
                ride.getLatOrigem(), ride.getLngOrigem(),
                ride.getLatDestino(), ride.getLngDestino(),
                ride.getDistanciaKm(), ride.getTempoMin(), ride.getPreco(),
                ride.getCategory(), ride.getPaymentMethod(), ride.getCreatedAt());
    }
}
