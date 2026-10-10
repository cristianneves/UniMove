package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * @param changeFor so para DINHEIRO: a nota com que o passageiro vai pagar
 *                  ("troco para R$ 50"). Precisa cobrir o valor da corrida.
 */
public record ConfirmPaymentRequest(
        @NotNull PaymentMethod method,

        @Positive
        @DecimalMax(value = "10000.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal changeFor
) {
    public ConfirmPaymentRequest(PaymentMethod method) {
        this(method, null);
    }
}
