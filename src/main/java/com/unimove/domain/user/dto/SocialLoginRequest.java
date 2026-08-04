package com.unimove.domain.user.dto;

import com.unimove.domain.user.social.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * O app faz o sign-in nativo no provedor e manda so o token de identidade.
 * O backend nunca ve senha nem faz redirect.
 */
public record SocialLoginRequest(
        @NotNull SocialProvider provider,
        @NotBlank String idToken
) {
}
