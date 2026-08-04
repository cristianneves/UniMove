package com.unimove.domain.user.social;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Mensagem deliberadamente generica: o motivo exato (assinatura, audiencia,
 * expiracao) so interessa ao log, nunca ao cliente.
 */
public class InvalidSocialTokenException extends BusinessException {
    public InvalidSocialTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Não foi possível validar sua conta no provedor.");
    }
}
