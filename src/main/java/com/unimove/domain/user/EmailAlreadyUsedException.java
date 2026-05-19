package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyUsedException extends BusinessException {
    public EmailAlreadyUsedException(String email) {
        super(HttpStatus.CONFLICT, "Já existe um cadastro com o email '" + email + "'.");
    }
}
