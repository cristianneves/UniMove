package com.unimove.domain.user.document;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.List;

class DriverDocumentationIncompleteException extends BusinessException {
    DriverDocumentationIncompleteException(DocumentationStatus status, List<String> pending) {
        super(HttpStatus.CONFLICT, "Documentação do motorista não está aprovada (" + status + ")"
                + (pending.isEmpty() ? "." : ": " + String.join("; ", pending) + "."));
    }
}
