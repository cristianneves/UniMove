package com.unimove.domain.user.dto;

import com.unimove.domain.user.Role;
import com.unimove.domain.user.User;
import com.unimove.shared.security.JwtService;

import java.time.Instant;
import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        Role role,
        String cidade,
        Instant expiresAt
) {
    /** Ponto unico de montagem: cadastro, login e login social devolvem o mesmo corpo. */
    public static AuthResponse of(User user, JwtService.IssuedToken issued) {
        return new AuthResponse(
                issued.token(),
                user.getId(),
                user.getRole(),
                user.getCidade(),
                issued.expiresAt()
        );
    }
}
