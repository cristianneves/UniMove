package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidEarningsRangeException extends BusinessException {
    public InvalidEarningsRangeException() {
        super(HttpStatus.BAD_REQUEST, "Período inválido: 'from' não pode ser posterior a 'to'.");
    }
}
