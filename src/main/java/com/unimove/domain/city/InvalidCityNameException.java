package com.unimove.domain.city;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Nome que nao produz slug algum apos a normalizacao (ex: "///"). */
public class InvalidCityNameException extends BusinessException {
    public InvalidCityNameException() {
        super(HttpStatus.BAD_REQUEST, "Nome de cidade inválido.");
    }
}
