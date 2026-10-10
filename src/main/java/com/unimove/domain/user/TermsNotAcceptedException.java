package com.unimove.domain.user;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Cadastro/reaceite com versao de termos diferente da vigente (HTTP 400). */
public class TermsNotAcceptedException extends BusinessException {
    public TermsNotAcceptedException(String currentVersion) {
        super(HttpStatus.BAD_REQUEST,
                "É preciso aceitar a versão vigente dos termos de uso e da política de privacidade ("
                        + currentVersion + ").");
    }
}
