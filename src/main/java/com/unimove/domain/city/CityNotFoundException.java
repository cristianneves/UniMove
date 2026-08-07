package com.unimove.domain.city;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Admin tentou desativar um slug que nao existe na tabela. */
public class CityNotFoundException extends BusinessException {
    public CityNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Cidade não encontrada.");
    }
}
