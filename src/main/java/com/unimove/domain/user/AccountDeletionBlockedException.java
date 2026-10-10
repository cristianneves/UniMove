package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountDeletionBlockedException extends BusinessException {
    public AccountDeletionBlockedException(HttpStatus status, String message) {
        super(status, message);
    }

    public static AccountDeletionBlockedException activeRide() {
        return new AccountDeletionBlockedException(HttpStatus.CONFLICT,
                "Conclua ou cancele a corrida em andamento antes de excluir a conta.");
    }

    public static AccountDeletionBlockedException admin() {
        return new AccountDeletionBlockedException(HttpStatus.FORBIDDEN,
                "Conta administrativa não pode ser excluída pelo app.");
    }
}
