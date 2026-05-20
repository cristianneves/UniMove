package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class IllegalRideTransitionException extends BusinessException {
    public IllegalRideTransitionException(RideStatus from, RideStatus to) {
        super(HttpStatus.CONFLICT,
                "Transição inválida da corrida: " + from + " → " + to + ".");
    }
}
