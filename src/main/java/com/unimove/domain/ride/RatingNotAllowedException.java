package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class RatingNotAllowedException extends BusinessException {
    public RatingNotAllowedException(RideStatus current) {
        super(HttpStatus.CONFLICT,
                "Avaliação só é permitida após a corrida ser concluída. Status atual: " + current + ".");
    }
}
