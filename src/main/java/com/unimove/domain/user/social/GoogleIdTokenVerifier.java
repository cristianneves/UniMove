package com.unimove.domain.user.social;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.List;

/**
 * Valida o id_token que o app Flutter obtem no sign-in nativo do Google.
 *
 * <p>A divisao de responsabilidade e proposital: o {@link JwtDecoder} injetado
 * (montado em {@code SocialConfig}) cuida de assinatura, emissor e expiracao;
 * aqui ficam as regras que precisam ser testaveis sem rede — audiencia e
 * extracao dos claims.
 */
public class GoogleIdTokenVerifier implements SocialIdentityVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

    private final JwtDecoder jwtDecoder;
    private final List<String> acceptedClientIds;

    public GoogleIdTokenVerifier(JwtDecoder jwtDecoder, List<String> acceptedClientIds) {
        if (acceptedClientIds == null || acceptedClientIds.isEmpty()) {
            throw new IllegalStateException(
                    "app.social.google.enabled=true exige app.social.google.client-ids "
                            + "(client ID de cada plataforma do app).");
        }
        this.jwtDecoder = jwtDecoder;
        this.acceptedClientIds = List.copyOf(acceptedClientIds);
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public SocialIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException e) {
            log.warn("id_token do Google rejeitado: {}", e.getMessage());
            throw new InvalidSocialTokenException();
        }

        // Sem esta checagem, um id_token valido emitido para OUTRO app Google
        // seria aceito aqui — e o atacante controlaria o e-mail dentro dele.
        if (jwt.getAudience().stream().noneMatch(acceptedClientIds::contains)) {
            log.warn("id_token do Google com audiencia inesperada: {}", jwt.getAudience());
            throw new InvalidSocialTokenException();
        }

        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            log.warn("id_token do Google sem sub/email — escopo de e-mail ausente no cliente?");
            throw new InvalidSocialTokenException();
        }

        return new SocialIdentity(
                SocialProvider.GOOGLE,
                subject,
                email.trim().toLowerCase(),
                emailVerified(jwt),
                jwt.getClaimAsString("name")
        );
    }

    /** O Google ja mandou {@code email_verified} como boolean e como string. */
    private static boolean emailVerified(Jwt jwt) {
        Object claim = jwt.getClaim("email_verified");
        if (claim instanceof Boolean bool) {
            return bool;
        }
        return claim != null && Boolean.parseBoolean(claim.toString());
    }
}
