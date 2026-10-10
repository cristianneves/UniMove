package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Motorista aprovado, mas sem CNH valida cadastrada — nao opera (HTTP 403). */
public class DriverDocumentationInvalidException extends BusinessException {
    public DriverDocumentationInvalidException() {
        super(HttpStatus.FORBIDDEN,
                "CNH vencida ou documentação incompleta. Atualize seus documentos para voltar a operar.");
    }
}
