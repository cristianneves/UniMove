package com.unimove.domain.ride.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EarningsResponse(
        LocalDate from,
        LocalDate to,
        long totalRides,
        BigDecimal grossEarnings,
        BigDecimal averagePerRide,
        List<EarningsDayItem> byDay
) {}
