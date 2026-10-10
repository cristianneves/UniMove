package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PassengerCannotCancelInProgressException extends BusinessException {
    public PassengerCannotCancelInProgressException() {
        super(HttpStatus.CONFLICT,
                "Corrida em andamento não pode ser cancelada pelo passageiro. Peça ao motorista para encerrar.");
    }
}
