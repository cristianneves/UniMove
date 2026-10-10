package com.unimove.domain.user.document;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

class DriverDocumentNotFoundException extends BusinessException {
    DriverDocumentNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Documento não encontrado.");
    }
}
