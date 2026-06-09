package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidCurrentPasswordException extends BusinessException {
    public InvalidCurrentPasswordException() {
        super(HttpStatus.BAD_REQUEST, "Senha atual incorreta.");
    }
}
