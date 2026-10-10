package com.unimove.domain.user.document;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

class DocumentUploadNotAllowedException extends BusinessException {
    DocumentUploadNotAllowedException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
