package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyUsedException extends BusinessException {
    public EmailAlreadyUsedException() {
        super(HttpStatus.CONFLICT, "E-mail já cadastrado.");
    }
}
