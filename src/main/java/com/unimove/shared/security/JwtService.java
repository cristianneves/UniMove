package com.unimove.shared.security;

import com.unimove.domain.user.Role;
import com.unimove.domain.user.User;
import com.unimove.shared.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String DEV_SECRET_PREFIX = "dev-secret";

    private final JwtProperties properties;
    private final Environment environment;
    private final SecretKey key;

    public JwtService(JwtProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @PostConstruct
    void assertSecretSafeInProd() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProd && properties.secret().startsWith(DEV_SECRET_PREFIX)) {
            throw new IllegalStateException(
                    "JWT_SECRET nao definido em producao. Configure a variavel de ambiente JWT_SECRET com >=256 bits."
            );
        }
    }

    public IssuedToken generate(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(properties.expirationMs());

        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("cidade", user.getCidade())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();

        return new IssuedToken(token, expiresAt);
    }

    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                claims.get("email", String.class),
                Role.valueOf(claims.get("role", String.class)),
                claims.get("cidade", String.class)
        );
    }

    public record IssuedToken(String token, Instant expiresAt) {}
}
