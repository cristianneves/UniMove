package com.unimove.domain.ride.dto;

import java.math.BigDecimal;

/** Projecao da situacao dos recebimentos no periodo do painel admin. */
public record PaymentMetricsAggregate(
        BigDecimal cashConfirmed,
        Long cashPending,
        Long cashDivergent,
        Long pixSimulated
) {}
