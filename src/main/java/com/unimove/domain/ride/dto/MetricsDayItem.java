package com.unimove.domain.ride.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Linha da série diária do painel admin: corridas criadas no dia, quantas viraram
 * COMPLETED e a receita correspondente. Base de data única = created_at.
 */
public record MetricsDayItem(
        LocalDate day,
        long totalRides,
        long completedRides,
        BigDecimal revenue
) {}
