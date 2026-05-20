package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class LocationUpdateNotAllowedException extends BusinessException {
    public LocationUpdateNotAllowedException(RideStatus current) {
        super(HttpStatus.CONFLICT,
                "Localização do motorista só pode ser atualizada nas fases DRIVER_EN_ROUTE ou IN_PROGRESS (atual: " + current + ").");
    }
}
