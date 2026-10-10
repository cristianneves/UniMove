package com.unimove.domain.audit.dto;

import com.unimove.domain.audit.AuditAction;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        UUID adminId,
        AuditAction action,
        String targetType,
        String targetId,
        String details,
        Instant createdAt
) {}
