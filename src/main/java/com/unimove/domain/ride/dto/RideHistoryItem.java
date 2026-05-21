package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.PaymentMethod;
import com.unimove.domain.ride.RideStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RideHistoryItem(
        UUID id,
        RideStatus status,
        String cidade,
        BigDecimal latOrigem,
        BigDecimal lngOrigem,
        BigDecimal latDestino,
        BigDecimal lngDestino,
        BigDecimal distanciaKm,
        Integer tempoMin,
        BigDecimal preco,
        PaymentMethod paymentMethod,
        UUID passageiroId,
        UUID motoristaId,
        Instant createdAt,
        Instant completedAt,
        Instant cancelledAt
) {}
