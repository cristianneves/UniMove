package com.unimove.domain.user.social;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Sem e-mail verificado no provedor nao ha como vincular a uma conta existente
 * pelo e-mail — seria sequestro de conta.
 */
public class SocialEmailNotVerifiedException extends BusinessException {
    public SocialEmailNotVerifiedException() {
        super(HttpStatus.FORBIDDEN, "O e-mail da conta no provedor precisa estar verificado.");
    }
}
