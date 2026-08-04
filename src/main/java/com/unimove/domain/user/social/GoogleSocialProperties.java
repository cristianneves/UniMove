package com.unimove.domain.user.social;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Credenciais do login com Google.
 *
 * @param enabled    liga o endpoint. Desligado por padrao para dev/testes
 *                   rodarem sem credencial.
 * @param clientIds  audiencias aceitas no claim {@code aud}. E uma LISTA de
 *                   proposito: o {@code aud} muda por plataforma (Android com
 *                   {@code serverClientId} devolve o client ID web, iOS devolve
 *                   o client ID iOS). Aceitar so um valor quebra assim que a
 *                   segunda plataforma entra.
 * @param jwkSetUri  JWKS publico do Google; o decoder cuida de cache e rotacao.
 */
@Validated
@ConfigurationProperties(prefix = "app.social.google")
public record GoogleSocialProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue List<String> clientIds,
        @DefaultValue("https://www.googleapis.com/oauth2/v3/certs") String jwkSetUri
) {
    /** Emissores validos de id_token do Google — os dois valores sao publicados. */
    public static final List<String> ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");
}
