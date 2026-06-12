package com.unimove.domain.ride.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resposta do painel admin de métricas (GET /admin/metrics). Agrega corridas,
 * receita e usuários no período [from, to] (por data de criação da corrida),
 * mais a série diária para gráficos.
 */
public record AdminMetricsResponse(
        LocalDate from,
        LocalDate to,
        RidesSummary rides,
        RevenueSummary revenue,
        UsersSummary users,
        List<MetricsDayItem> byDay
) {

    /** Volume e desfecho das corridas no período. Taxas em fração [0,1]. */
    public record RidesSummary(
            long total,
            long completed,
            long cancelled,
            long expired,
            long active,
            double completionRate,
            double cancellationRate
    ) {}

    /** Receita reconhecida no período. */
    public record RevenueSummary(
            BigDecimal gross,
            BigDecimal cancellationFees,
            BigDecimal averageTicket
    ) {}

    /** Fotografia atual da base (não filtrada por período — é estado corrente). */
    public record UsersSummary(
            long totalPassengers,
            long totalDrivers,
            long onlineDrivers,
            long pendingDrivers,
            long suspendedUsers
    ) {}
}
