package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class RideAccessDeniedException extends BusinessException {
    public RideAccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "Você não tem acesso a esta corrida.");
    }
}
