package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

/**
 * O preco recalculado no pedido difere do que o passageiro viu na estimativa
 * (surge mudou no meio). 409 para o app mostrar o valor novo e pedir
 * confirmacao — nunca cobrar um valor diferente em silencio.
 */
public class PriceChangedException extends BusinessException {
    public PriceChangedException(BigDecimal expected, BigDecimal current) {
        super(HttpStatus.CONFLICT, "O preço mudou de R$ " + expected + " para R$ " + current
                + ". Confirme o novo valor para pedir a corrida.");
    }
}
