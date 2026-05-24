package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UserSuspendedException extends BusinessException {
    public UserSuspendedException() {
        super(HttpStatus.FORBIDDEN,
                "Conta suspensa. Entre em contato com o suporte para mais informações.");
    }
}
