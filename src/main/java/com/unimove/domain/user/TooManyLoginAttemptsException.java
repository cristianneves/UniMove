package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class TooManyLoginAttemptsException extends BusinessException {

    public TooManyLoginAttemptsException(long minutesRemaining) {
        super(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas de login. Tente novamente em " + minutesRemaining + " minuto(s).");
    }
}
