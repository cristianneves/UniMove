package com.unimove.domain.ride.dto;

import java.math.BigDecimal;

public record EstimateResponse(
        BigDecimal distanciaKm,
        Integer tempoMin,
        BigDecimal preco
) {}
