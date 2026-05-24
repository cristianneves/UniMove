package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CannotSuspendAdminException extends BusinessException {
    public CannotSuspendAdminException() {
        super(HttpStatus.FORBIDDEN, "Não é permitido suspender uma conta ADMIN.");
    }
}
