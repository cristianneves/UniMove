package com.unimove.domain.verification;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Token de verificacao ausente, invalido, vencido ou ja usado. HTTP 400 porque
 * o cliente precisa refazer o desafio — nao e falha de autenticacao.
 */
public class PhoneVerificationRequiredException extends BusinessException {

    public PhoneVerificationRequiredException() {
        this("Telefone nao verificado. Refaca a verificacao pelo WhatsApp antes de concluir o cadastro.");
    }

    public PhoneVerificationRequiredException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
