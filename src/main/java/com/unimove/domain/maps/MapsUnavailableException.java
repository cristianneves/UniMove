package com.unimove.domain.maps;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class MapsUnavailableException extends BusinessException {

    public MapsUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public MapsUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
        initCause(cause);
    }
}
