package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class DriverNotFoundException extends BusinessException {
    public DriverNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Perfil de motorista não encontrado.");
    }
}
