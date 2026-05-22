package com.unimove.domain.ride;

import com.unimove.domain.user.Role;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Quem paga taxa em qual cenario:
 *
 *  PENDING_PAYMENT       → 0 (sem motorista envolvido ainda)
 *  AVAILABLE_IN_MURAL    → 0 (motorista ainda nao aceitou)
 *  DRIVER_EN_ROUTE
 *    └ passageiro cancela <= 120s do accept → 0 (janela de graca)
 *    └ passageiro cancela >  120s do accept → R$ 3,00
 *    └ motorista  cancela                   → 0 (registrado em cancelled_by p/ futuras politicas de strike)
 *
 * Formula centralizada para ajuste — alterar aqui propaga para todos os
 * cancelamentos novos. Cancelamentos antigos ficam imutaveis no banco.
 */
@Component
public class CancellationPolicy {

    static final BigDecimal PASSENGER_LATE_FEE = new BigDecimal("3.00");
    static final long GRACE_PERIOD_SECONDS = 120;

    public BigDecimal computeFee(Role role, RideStatus status, Instant acceptedAt, Instant now) {
        if (role != Role.PASSAGEIRO) {
            return BigDecimal.ZERO;
        }
        if (status != RideStatus.DRIVER_EN_ROUTE) {
            return BigDecimal.ZERO;
        }
        if (acceptedAt == null) {
            return BigDecimal.ZERO;
        }
        long elapsed = Duration.between(acceptedAt, now).toSeconds();
        return elapsed > GRACE_PERIOD_SECONDS ? PASSENGER_LATE_FEE : BigDecimal.ZERO;
    }
}
