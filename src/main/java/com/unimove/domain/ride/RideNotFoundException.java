package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class RideNotFoundException extends BusinessException {
    public RideNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Corrida não encontrada.");
    }
}
