package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class RatingAlreadySubmittedException extends BusinessException {
    public RatingAlreadySubmittedException() {
        super(HttpStatus.CONFLICT, "Você já avaliou esta corrida.");
    }
}
