package com.unimove.domain.user.social;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * O decoder e mockado de proposito: assinatura, emissor e expiracao sao
 * responsabilidade dele (montado em {@code SocialConfig}). Aqui testamos o que
 * e regra nossa — audiencia e leitura dos claims — sem depender de rede.
 */
@ExtendWith(MockitoExtension.class)
class GoogleIdTokenVerifierTest {

    private static final String ANDROID_CLIENT_ID = "111-android.apps.googleusercontent.com";
    private static final String IOS_CLIENT_ID = "222-ios.apps.googleusercontent.com";
    private static final String RAW_TOKEN = "eyJ-token-do-app";

    @Mock JwtDecoder jwtDecoder;

    private GoogleIdTokenVerifier verifier() {
        return new GoogleIdTokenVerifier(jwtDecoder, List.of(ANDROID_CLIENT_ID, IOS_CLIENT_ID));
    }

    private static Jwt jwt(String audience, Map<String, Object> extraClaims) {
        Jwt.Builder builder = Jwt.withTokenValue(RAW_TOKEN)
                .header("alg", "RS256")
                .subject("google-sub-123")
                .audience(List.of(audience))
                .issuer("https://accounts.google.com")
                .issuedAt(Instant.parse("2026-08-04T12:00:00Z"))
                .expiresAt(Instant.parse("2026-08-04T13:00:00Z"));
        extraClaims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    @DisplayName("token válido vira SocialIdentity com e-mail normalizado")
    void validTokenIsMappedToIdentity() {
        when(jwtDecoder.decode(RAW_TOKEN)).thenReturn(jwt(ANDROID_CLIENT_ID, Map.of(
                "email", "  Maria@Example.COM ",
                "email_verified", true,
                "name", "Maria Silva"
        )));

        SocialIdentity identity = verifier().verify(RAW_TOKEN);

        assertThat(identity.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(identity.subject()).isEqualTo("google-sub-123");
        assertThat(identity.email()).isEqualTo("maria@example.com");
        assertThat(identity.emailVerified()).isTrue();
        assertThat(identity.name()).isEqualTo("Maria Silva");
    }

    @Test
    @DisplayName("aceita qualquer client ID configurado (uma audiência por plataforma)")
    void acceptsAnyConfiguredAudience() {
        when(jwtDecoder.decode(RAW_TOKEN)).thenReturn(jwt(IOS_CLIENT_ID, Map.of(
                "email", "joao@example.com", "email_verified", true
        )));

        assertThat(verifier().verify(RAW_TOKEN).email()).isEqualTo("joao@example.com");
    }

    @Test
    @DisplayName("email_verified como string (o Google já mandou dos dois jeitos)")
    void emailVerifiedAsStringIsUnderstood() {
        when(jwtDecoder.decode(RAW_TOKEN)).thenReturn(jwt(ANDROID_CLIENT_ID, Map.of(
                "email", "joao@example.com", "email_verified", "true"
        )));

        assertThat(verifier().verify(RAW_TOKEN).emailVerified()).isTrue();
    }

    @Test
    @DisplayName("token emitido para OUTRO app Google é rejeitado")
    void tokenForAnotherAudienceIsRejected() {
        when(jwtDecoder.decode(RAW_TOKEN)).thenReturn(jwt("999-app-do-atacante.apps.googleusercontent.com",
                Map.of("email", "atacante@example.com", "email_verified", true)));

        assertThatThrownBy(() -> verifier().verify(RAW_TOKEN))
                .isInstanceOf(InvalidSocialTokenException.class);
    }

    @Test
    @DisplayName("assinatura/expiração inválidas no decoder viram 401 de domínio")
    void decoderFailureBecomesDomainException() {
        when(jwtDecoder.decode(RAW_TOKEN)).thenThrow(new BadJwtException("assinatura inválida"));

        assertThatThrownBy(() -> verifier().verify(RAW_TOKEN))
                .isInstanceOf(InvalidSocialTokenException.class);
    }

    @Test
    @DisplayName("token sem claim de e-mail é rejeitado")
    void tokenWithoutEmailIsRejected() {
        when(jwtDecoder.decode(RAW_TOKEN)).thenReturn(jwt(ANDROID_CLIENT_ID, Map.of("email_verified", true)));

        assertThatThrownBy(() -> verifier().verify(RAW_TOKEN))
                .isInstanceOf(InvalidSocialTokenException.class);
    }

    @Test
    @DisplayName("e-mail não verificado passa pelo verifier — quem recusa é o service")
    void unverifiedEmailIsReportedNotThrown() {
        when(jwtDecoder.decode(RAW_TOKEN)).thenReturn(jwt(ANDROID_CLIENT_ID, Map.of(
                "email", "joao@example.com", "email_verified", false
        )));

        assertThat(verifier().verify(RAW_TOKEN).emailVerified()).isFalse();
    }

    @Test
    @DisplayName("sem client ID configurado o bean nem nasce")
    void missingClientIdsFailsFast() {
        assertThatThrownBy(() -> new GoogleIdTokenVerifier(jwtDecoder, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-ids");
    }
}
