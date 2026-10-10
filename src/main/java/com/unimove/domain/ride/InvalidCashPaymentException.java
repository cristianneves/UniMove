package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Dados de pagamento em dinheiro incoerentes com a corrida (HTTP 400). */
public class InvalidCashPaymentException extends BusinessException {
    public InvalidCashPaymentException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
