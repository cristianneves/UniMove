package com.unimove.shared.security;

import com.unimove.domain.user.Role;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String email,
        Role role,
        String cidade
) {}
