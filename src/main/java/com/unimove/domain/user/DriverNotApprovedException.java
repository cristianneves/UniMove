package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class DriverNotApprovedException extends BusinessException {
    public DriverNotApprovedException() {
        super(HttpStatus.FORBIDDEN,
                "Cadastro de motorista ainda não foi aprovado pelo administrador.");
    }
}
