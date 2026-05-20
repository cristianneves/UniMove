package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidCityException extends BusinessException {
    public InvalidCityException() {
        super(HttpStatus.BAD_REQUEST, "Cidade inválida.");
    }
}
