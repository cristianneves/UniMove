package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidMetricsRangeException extends BusinessException {
    public InvalidMetricsRangeException() {
        super(HttpStatus.BAD_REQUEST, "Período inválido: 'from' não pode ser posterior a 'to'.");
    }
}
