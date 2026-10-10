package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidPreferenceException extends BusinessException {
    public InvalidPreferenceException() {
        super(HttpStatus.BAD_REQUEST, "Pagamento e categoria preferidos são só para passageiro.");
    }
}
