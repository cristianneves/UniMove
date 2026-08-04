package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Conta criada por login social ainda nao tem senha. Definir uma sem ter a
 * atual esta fora do MVP — o caminho e o reset pelo admin.
 */
public class PasswordNotSetException extends BusinessException {
    public PasswordNotSetException() {
        super(HttpStatus.CONFLICT,
                "Esta conta entra pelo provedor social e ainda não tem senha. Peça uma senha temporária ao suporte.");
    }
}
