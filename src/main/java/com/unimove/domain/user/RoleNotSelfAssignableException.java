package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class RoleNotSelfAssignableException extends BusinessException {
    public RoleNotSelfAssignableException() {
        super(HttpStatus.FORBIDDEN, "Não é permitido cadastrar-se com este perfil.");
    }
}
