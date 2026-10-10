package com.unimove.domain.user.document.dto;

import com.unimove.domain.user.document.DriverDocumentType;

import java.time.Instant;
import java.util.UUID;

/** Envio aguardando analise na fila do admin. */
public record PendingDocumentItem(
        UUID documentId,
        UUID driverId,
        String driverName,
        String cidade,
        DriverDocumentType type,
        Instant submittedAt
) {}
