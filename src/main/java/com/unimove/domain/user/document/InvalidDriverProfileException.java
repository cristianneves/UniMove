package com.unimove.domain.user.document;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

class InvalidDriverProfileException extends BusinessException {
    InvalidDriverProfileException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
