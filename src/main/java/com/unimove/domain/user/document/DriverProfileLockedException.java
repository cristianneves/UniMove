package com.unimove.domain.user.document;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

class DriverProfileLockedException extends BusinessException {
    DriverProfileLockedException() {
        super(HttpStatus.CONFLICT,
                "Cadastro aprovado não pode ser alterado pelo app. Fale com o suporte para atualizar seus dados.");
    }
}
