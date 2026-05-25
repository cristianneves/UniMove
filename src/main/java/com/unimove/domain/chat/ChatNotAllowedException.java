package com.unimove.domain.chat;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ChatNotAllowedException extends BusinessException {
    public ChatNotAllowedException(String reason) {
        super(HttpStatus.FORBIDDEN, reason);
    }
}
