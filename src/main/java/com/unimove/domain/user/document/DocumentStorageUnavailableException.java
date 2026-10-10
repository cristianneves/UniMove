package com.unimove.domain.user.document;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

class DocumentStorageUnavailableException extends BusinessException {
    DocumentStorageUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                "Envio de documentos indisponível no momento. Tente novamente em alguns minutos.");
    }
}
