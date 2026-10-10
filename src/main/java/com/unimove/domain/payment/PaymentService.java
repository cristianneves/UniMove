package com.unimove.domain.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentService {

    /**
     * Pix aceito como forma de pagamento? Sem PSP o Pix e so simulado: nenhum
     * dinheiro circula e o motorista rodaria de graca. No piloto so dinheiro
     * fica desligado ({@code PAYMENT_PIX_ENABLED=false}).
     */
    boolean isPixEnabled();

    String generatePixPayload(UUID rideId, BigDecimal valor);
}
