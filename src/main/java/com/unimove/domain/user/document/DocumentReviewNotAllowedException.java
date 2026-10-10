package com.unimove.domain.user.document;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

class DocumentReviewNotAllowedException extends BusinessException {
    DocumentReviewNotAllowedException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
