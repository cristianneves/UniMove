package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CannotResetAdminPasswordException extends BusinessException {
    public CannotResetAdminPasswordException() {
        super(HttpStatus.FORBIDDEN, "Não é permitido resetar a senha de uma conta ADMIN.");
    }
}
