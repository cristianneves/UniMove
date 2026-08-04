package com.unimove.domain.user.social;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Liga o login social por configuracao. Sem
 * {@code app.social.google.enabled=true} nenhum bean nasce e os endpoints
 * respondem 503 — e o que permite dev e testes rodarem sem credencial.
 */
@Configuration
@EnableConfigurationProperties(GoogleSocialProperties.class)
class SocialConfig {

    @Bean
    @ConditionalOnProperty(name = "app.social.google.enabled", havingValue = "true")
    SocialIdentityVerifier googleIdTokenVerifier(GoogleSocialProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.jwkSetUri()).build();
        decoder.setJwtValidator(googleTokenValidator());
        // O construtor derruba o startup se os client IDs faltarem — melhor
        // falhar aqui do que no primeiro login de producao.
        return new GoogleIdTokenVerifier(decoder, props.clientIds());
    }

    /**
     * Expiracao + emissor. A audiencia fica no verifier, onde e testavel sem
     * rede. O {@code iss} pode chegar como String ou URL dependendo do parse
     * do Nimbus, dai a comparacao por {@code toString()}.
     */
    private static OAuth2TokenValidator<Jwt> googleTokenValidator() {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                        iss -> iss != null && GoogleSocialProperties.ISSUERS.contains(iss.toString()))
        );
    }
}
