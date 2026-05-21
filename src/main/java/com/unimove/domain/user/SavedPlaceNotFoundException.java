package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SavedPlaceNotFoundException extends BusinessException {
    public SavedPlaceNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Endereço favorito não encontrado.");
    }
}
