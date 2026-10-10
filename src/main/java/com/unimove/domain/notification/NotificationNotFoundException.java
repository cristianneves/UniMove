package com.unimove.domain.notification;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class NotificationNotFoundException extends BusinessException {
    public NotificationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Notificação não encontrada.");
    }
}
