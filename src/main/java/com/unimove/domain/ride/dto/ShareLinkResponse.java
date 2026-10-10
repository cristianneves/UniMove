package com.unimove.domain.ride.dto;

import java.util.UUID;

/** Link publico de acompanhamento da viagem; o app monta a URL com o host. */
public record ShareLinkResponse(UUID token, String path) {
    public static ShareLinkResponse of(UUID token) {
        return new ShareLinkResponse(token, "/share/" + token);
    }
}
