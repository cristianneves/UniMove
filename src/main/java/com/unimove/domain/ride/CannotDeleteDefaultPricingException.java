package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CannotDeleteDefaultPricingException extends BusinessException {
    public CannotDeleteDefaultPricingException() {
        super(HttpStatus.FORBIDDEN,
                "A configuração padrão (_DEFAULT) não pode ser removida — apenas editada.");
    }
}
