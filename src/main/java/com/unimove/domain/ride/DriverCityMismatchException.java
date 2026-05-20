package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class DriverCityMismatchException extends BusinessException {
    public DriverCityMismatchException() {
        super(HttpStatus.FORBIDDEN, "Esta corrida pertence a outra cidade.");
    }
}
