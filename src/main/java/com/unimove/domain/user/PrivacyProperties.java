package com.unimove.domain.user;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Versao vigente dos termos de uso + politica de privacidade. Trocar a versao
 * faz o cadastro exigir o aceite da nova; quem ja tem conta reaceita por
 * POST /users/me/terms.
 *
 * @param termsVersion versao publicada (ex.: "2026-10").
 * @param policyUrl    onde o texto esta publicado (o app abre este link).
 */
@Validated
@ConfigurationProperties(prefix = "app.privacy")
public record PrivacyProperties(
        @DefaultValue("2026-10") @NotBlank String termsVersion,
        @DefaultValue("https://unimove.app/privacidade") @NotBlank String policyUrl
) {}
