package com.unimove.domain.ride;

import com.unimove.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ShareLinkExpiredException extends BusinessException {
    public ShareLinkExpiredException() {
        super(HttpStatus.GONE, "Esta viagem já foi encerrada.");
    }
}
