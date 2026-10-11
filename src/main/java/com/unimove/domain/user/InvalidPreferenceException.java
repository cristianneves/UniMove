package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidPreferenceException extends BusinessException {
    public InvalidPreferenceException() {
        this("Pagamento e categoria preferidos são só para passageiro.");
    }

    private InvalidPreferenceException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    static InvalidPreferenceException pixUnavailable() {
        return new InvalidPreferenceException("Pix indisponível no momento. Escolha pagamento em dinheiro.");
    }
}
