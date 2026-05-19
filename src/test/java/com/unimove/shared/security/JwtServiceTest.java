package com.unimove.shared.security;

import com.unimove.domain.user.Role;
import com.unimove.domain.user.User;
import com.unimove.shared.config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW9ubHktbmV2ZXItdXNlLWluLXByb2QtZW52aXJvbm1lbnRz";

    private JwtService service;

    @BeforeEach
    void setUp() {
        service = new JwtService(new JwtProperties(SECRET, 3_600_000));
    }

    @Test
    void generateAndParseRoundTrip() {
        User user = sampleUser(Role.PASSAGEIRO, "sao-jose-do-rio-preto");

        JwtService.IssuedToken issued = service.generate(user);

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.expiresAt()).isAfter(Instant.now());

        AuthenticatedUser parsed = service.parse(issued.token());
        assertThat(parsed.userId()).isEqualTo(user.getId());
        assertThat(parsed.email()).isEqualTo(user.getEmail());
        assertThat(parsed.role()).isEqualTo(Role.PASSAGEIRO);
        assertThat(parsed.cidade()).isEqualTo("sao-jose-do-rio-preto");
    }

    @Test
    void parseRejectsTokenSignedWithDifferentSecret() {
        User user = sampleUser(Role.MOTORISTA, "campinas");
        String token = service.generate(user).token();

        JwtService otherService = new JwtService(new JwtProperties(
                "b3RoZXItc2VjcmV0LWtleS1mb3ItdGVzdGluZy1vbmx5LW5ldmVyLXVzZS1pbi1wcm9kLWVudmlyb25tZW50cw==",
                3_600_000
        ));

        assertThatThrownBy(() -> otherService.parse(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void parseRejectsExpiredToken() throws InterruptedException {
        JwtService shortLived = new JwtService(new JwtProperties(SECRET, 1));
        String token = shortLived.generate(sampleUser(Role.PASSAGEIRO, "campinas")).token();
        Thread.sleep(50);

        assertThatThrownBy(() -> shortLived.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseRejectsMalformedToken() {
        assertThatThrownBy(() -> service.parse("nao-eh-um-jwt"))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    private User sampleUser(Role role, String cidade) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("user@example.com");
        u.setName("User");
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setCidade(cidade);
        u.setCreatedAt(Instant.now());
        return u;
    }
}
