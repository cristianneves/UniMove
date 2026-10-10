package com.unimove.domain.user.dto;

import com.unimove.domain.user.Role;
import com.unimove.domain.user.User;
import com.unimove.domain.user.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Linha da busca de usuarios do admin. */
public record AdminUserItem(
        UUID userId,
        String name,
        String email,
        String phone,
        Role role,
        String cidade,
        UserStatus status,
        BigDecimal ratingAvg,
        Integer ratingCount,
        Instant createdAt
) {
    public static AdminUserItem from(User u) {
        return new AdminUserItem(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.getCidade(),
                u.getStatus(), u.getRatingAvg(), u.getRatingCount(), u.getCreatedAt());
    }
}
