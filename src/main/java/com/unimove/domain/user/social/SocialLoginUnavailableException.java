package com.unimove.domain.user.social;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Provedor nao configurado neste ambiente (ex.: dev sem client ID). */
public class SocialLoginUnavailableException extends BusinessException {
    public SocialLoginUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Login social não está disponível neste ambiente.");
    }
}
