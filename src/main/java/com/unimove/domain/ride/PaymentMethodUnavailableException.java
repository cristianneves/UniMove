package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Forma de pagamento desligada no ambiente (Pix sem PSP no piloto so dinheiro). HTTP 409. */
public class PaymentMethodUnavailableException extends BusinessException {
    public PaymentMethodUnavailableException() {
        super(HttpStatus.CONFLICT, "Pix indisponível no momento. Escolha pagamento em dinheiro.");
    }
}
