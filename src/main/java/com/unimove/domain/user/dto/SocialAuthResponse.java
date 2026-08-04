package com.unimove.domain.user.dto;

/**
 * Resposta discriminada por {@code status}: usuario novo nao e erro, e um
 * caminho previsto do fluxo. O app ramifica uma vez e segue.
 *
 * <ul>
 *   <li>{@code AUTHENTICATED} — {@code auth} preenchido, {@code profile} nulo.</li>
 *   <li>{@code REGISTRATION_REQUIRED} — {@code profile} preenchido para
 *       pre-preencher o formulario; o app segue para o desafio do WhatsApp e
 *       depois chama {@code POST /auth/social/register}.</li>
 * </ul>
 */
public record SocialAuthResponse(
        Status status,
        AuthResponse auth,
        SocialProfile profile
) {
    public enum Status { AUTHENTICATED, REGISTRATION_REQUIRED }

    public static SocialAuthResponse authenticated(AuthResponse auth) {
        return new SocialAuthResponse(Status.AUTHENTICATED, auth, null);
    }

    public static SocialAuthResponse registrationRequired(String email, String name) {
        return new SocialAuthResponse(Status.REGISTRATION_REQUIRED, null, new SocialProfile(email, name));
    }
}
