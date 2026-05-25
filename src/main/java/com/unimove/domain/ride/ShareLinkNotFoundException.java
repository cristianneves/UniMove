package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ShareLinkNotFoundException extends BusinessException {
    public ShareLinkNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Link de compartilhamento inválido.");
    }
}
