package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CategoryMismatchException extends BusinessException {
    public CategoryMismatchException(RideCategory rideCategory, RideCategory driverCategory) {
        super(HttpStatus.CONFLICT,
                "Esta corrida é da categoria " + rideCategory
                        + " e seu veículo é " + driverCategory + ".");
    }
}
