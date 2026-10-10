package com.unimove.domain.ride.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Registro do motorista sobre o dinheiro recebido na corrida.
 *
 * @param receivedAmount quanto ficou com o motorista pela corrida (sem o troco devolvido).
 * @param note           obrigatoria quando o valor for menor que o da corrida.
 */
public record CashPaymentRequest(
        @NotNull
        @DecimalMin(value = "0.00")
        @DecimalMax(value = "10000.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal receivedAmount,

        @Size(max = 500)
        String note
) {}
