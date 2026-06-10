package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Usuario ja tem uma corrida ativa: passageiro nao cria outra enquanto a atual
 * nao termina; motorista nao aceita outra enquanto esta em corrida (HTTP 409).
 */
public class ActiveRideExistsException extends BusinessException {

    private ActiveRideExistsException(String message) {
        super(HttpStatus.CONFLICT, message);
    }

    public static ActiveRideExistsException forPassenger() {
        return new ActiveRideExistsException(
                "Você já tem uma corrida em andamento. Conclua ou cancele antes de pedir outra.");
    }

    public static ActiveRideExistsException forDriver() {
        return new ActiveRideExistsException(
                "Você já tem uma corrida em andamento. Conclua antes de aceitar outra.");
    }
}
