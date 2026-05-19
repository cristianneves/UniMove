package com.unimove.domain.user.dto;

import com.unimove.domain.user.Role;

import java.time.Instant;
import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        Role role,
        String cidade,
        Instant expiresAt
) {}
