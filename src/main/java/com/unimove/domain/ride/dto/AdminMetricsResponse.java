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
        PaymentsSummary payments,
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

    /**
     * Valor COBRADO no periodo: {@code gross} soma o preco das corridas
     * concluidas, independente de o dinheiro ter entrado (inclui Pix simulado
     * e dinheiro pendente/divergente). O que de fato foi recebido esta em
     * {@link PaymentsSummary#cashConfirmed()}.
     */
    public record RevenueSummary(
            BigDecimal gross,
            BigDecimal cancellationFees,
            BigDecimal averageTicket
    ) {}

    /**
     * Recebimentos no periodo. {@code cashConfirmed} e o unico valor que de
     * fato entrou (registrado pelo motorista); Pix sem PSP aparece so como
     * contagem {@code pixSimulated} e nunca soma como receita recebida.
     * {@code cashPending}/{@code cashDivergent} sao a fila de conciliacao.
     */
    public record PaymentsSummary(
            BigDecimal cashConfirmed,
            long cashPending,
            long cashDivergent,
            long pixSimulated
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
