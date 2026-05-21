package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class DuplicateSavedPlaceLabelException extends BusinessException {
    public DuplicateSavedPlaceLabelException(String label) {
        super(HttpStatus.CONFLICT,
                "Você já possui um endereço favorito com o nome '" + label + "'.");
    }
}
