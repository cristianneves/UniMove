package com.unimove.domain.ride.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Extrato do motorista no periodo. {@code grossEarnings} e o valor das
 * corridas concluidas (o que foi cobrado); {@code payments} diz quanto disso
 * de fato entrou: so o dinheiro registrado e recebido. Pix sem PSP e
 * simulado e aparece separado — nao e dinheiro recebido.
 */
public record EarningsResponse(
        LocalDate from,
        LocalDate to,
        long totalRides,
        BigDecimal grossEarnings,
        BigDecimal averagePerRide,
        List<EarningsDayItem> byDay,
        PaymentBreakdown payments
) {
    /**
     * @param cashReceived       dinheiro que o motorista registrou ter recebido.
     * @param cashPending        valor das corridas em dinheiro ainda sem registro.
     * @param cashDivergentRides corridas em dinheiro recebidas a menor.
     * @param pixSimulated       valor das corridas Pix simuladas (sem recebimento real).
     */
    public record PaymentBreakdown(
            BigDecimal cashReceived,
            BigDecimal cashPending,
            long cashDivergentRides,
            BigDecimal pixSimulated
    ) {}
}
