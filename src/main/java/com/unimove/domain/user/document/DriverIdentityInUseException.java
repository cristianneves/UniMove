package com.unimove.domain.user.document;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

class DriverIdentityInUseException extends BusinessException {
    DriverIdentityInUseException() {
        super(HttpStatus.CONFLICT, "CPF ou CNH já cadastrado em outra conta de motorista.");
    }
}
