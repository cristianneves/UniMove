package com.unimove.domain.user.dto;

import java.time.Instant;

/**
 * Resposta da atualização de perfil. Como o JWT carrega a claim {@code cidade},
 * quando a cidade muda um novo token é emitido e retornado em {@code token}/
 * {@code tokenExpiresAt} — o cliente deve substituir o token atual. Quando a
 * cidade não muda, ambos vêm {@code null} e o token vigente continua válido.
 */
public record UpdateProfileResponse(
        UserProfileResponse profile,
        String token,
        Instant tokenExpiresAt
) {
}
