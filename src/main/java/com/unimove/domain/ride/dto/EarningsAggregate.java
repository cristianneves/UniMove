package com.unimove.domain.ride.dto;

import java.math.BigDecimal;

public record EarningsAggregate(
        long totalRides,
        BigDecimal grossEarnings,
        BigDecimal cashReceived,
        BigDecimal cashPending,
        Long cashDivergentRides,
        BigDecimal pixSimulated
) {
    public EarningsAggregate(long totalRides, BigDecimal grossEarnings) {
        this(totalRides, grossEarnings, BigDecimal.ZERO, BigDecimal.ZERO, 0L, BigDecimal.ZERO);
    }
}
