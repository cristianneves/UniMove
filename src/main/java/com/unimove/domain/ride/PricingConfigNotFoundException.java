package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PricingConfigNotFoundException extends BusinessException {
    public PricingConfigNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Configuração de tarifa não encontrada.");
    }
}
