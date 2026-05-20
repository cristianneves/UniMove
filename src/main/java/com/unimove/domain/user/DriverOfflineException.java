package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class DriverOfflineException extends BusinessException {
    public DriverOfflineException() {
        super(HttpStatus.FORBIDDEN, "Motorista precisa estar online para ver ou aceitar corridas.");
    }
}
