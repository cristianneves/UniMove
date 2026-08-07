package com.unimove.domain.city;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Distinta de {@code InvalidCityException} de proposito: aquela cobre entrada
 * malformada (vazia, so simbolos), esta cobre entrada bem formada para uma
 * cidade onde ainda nao operamos. A mensagem muda o que o usuario faz a seguir.
 */
public class CityNotServedException extends BusinessException {
    public CityNotServedException() {
        super(HttpStatus.BAD_REQUEST, "Ainda não atendemos esta cidade.");
    }
}
