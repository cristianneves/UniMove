package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Corrida nao esta em estado de receber o registro do dinheiro (HTTP 409). */
public class CashPaymentNotAllowedException extends BusinessException {
    public CashPaymentNotAllowedException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
