package com.unimove.domain.verification;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Teto de desafios por IP estourado. Espelha o 429 de
 * {@code TooManyLoginAttemptsException} no login.
 */
public class TooManyChallengesException extends BusinessException {

    public TooManyChallengesException() {
        super(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas de verificacao. Aguarde alguns minutos e tente novamente.");
    }
}
