package com.unimove.domain.ride.dto;

import java.math.BigDecimal;

/**
 * Projeção da agregação de corridas no período (uma única query). Contagens por
 * status e somatórios de receita; as taxas e o "active" são derivados no service.
 */
public record RideMetricsAggregate(
        long total,
        long completed,
        long cancelled,
        long expired,
        BigDecimal gross,
        BigDecimal cancellationFees
) {}
