package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PhoneAlreadyUsedException extends BusinessException {
    public PhoneAlreadyUsedException() {
        super(HttpStatus.CONFLICT, "Este telefone já está em uso em outra conta.");
    }
}
