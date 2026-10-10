package com.unimove.domain.notification.dto;

import com.unimove.domain.notification.CampaignStatus;
import com.unimove.domain.user.Role;

import java.time.Instant;
import java.util.UUID;

/** Historico da campanha: segmento, quem disparou, quantos receberam e quando. */
public record CampaignResponse(
        UUID id,
        String title,
        String body,
        String targetCidade,
        Role targetRole,
        CampaignStatus status,
        int recipients,
        UUID createdByAdminId,
        Instant createdAt,
        Instant sentAt
) {}
