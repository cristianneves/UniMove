package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Posicao recebida e sintaticamente valida mas nao confiavel para tracking (HTTP 422). */
public class InvalidDriverLocationException extends BusinessException {
    public InvalidDriverLocationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
