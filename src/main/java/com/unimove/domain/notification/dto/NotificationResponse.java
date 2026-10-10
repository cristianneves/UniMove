package com.unimove.domain.notification.dto;

import com.unimove.domain.notification.NotificationKind;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationKind kind,
        String title,
        String body,
        Instant createdAt,
        Instant readAt
) {}
