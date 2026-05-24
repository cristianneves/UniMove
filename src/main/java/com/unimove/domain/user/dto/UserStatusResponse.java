package com.unimove.domain.user.dto;

import com.unimove.domain.user.User;
import com.unimove.domain.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserStatusResponse(
        UUID userId,
        String name,
        String email,
        UserStatus status,
        Instant suspendedAt,
        String suspendedReason,
        UUID suspendedByAdminId
) {
    public static UserStatusResponse from(User u) {
        return new UserStatusResponse(
                u.getId(),
                u.getName(),
                u.getEmail(),
                u.getStatus(),
                u.getSuspendedAt(),
                u.getSuspendedReason(),
                u.getSuspendedByAdminId()
        );
    }
}
