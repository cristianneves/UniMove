package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.PaymentMethod;

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
        PaymentMethod paymentMethod,
        Instant createdAt
) {}
