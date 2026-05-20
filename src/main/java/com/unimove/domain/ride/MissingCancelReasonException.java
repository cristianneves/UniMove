package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class MissingCancelReasonException extends BusinessException {
    public MissingCancelReasonException() {
        super(HttpStatus.BAD_REQUEST,
                "Motorista precisa informar uma justificativa para cancelar a corrida.");
    }
}
