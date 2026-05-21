package com.unimove.domain.ride.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EarningsDayItem(
        LocalDate day,
        long rides,
        BigDecimal gross
) {}
