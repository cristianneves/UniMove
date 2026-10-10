package com.unimove.domain.user.document.dto;

import com.unimove.domain.user.document.DriverDocument;
import com.unimove.domain.user.document.DriverDocumentStatus;
import com.unimove.domain.user.document.DriverDocumentType;

import java.time.Instant;
import java.util.UUID;

/** Um envio de documento. Nunca carrega o arquivo nem o caminho no storage. */
public record DriverDocumentItem(
        UUID id,
        DriverDocumentType type,
        DriverDocumentStatus status,
        String rejectionReason,
        String contentType,
        Integer sizeBytes,
        Instant submittedAt,
        Instant reviewedAt
) {
    public static DriverDocumentItem from(DriverDocument d) {
        return new DriverDocumentItem(d.getId(), d.getType(), d.getStatus(), d.getRejectionReason(),
                d.getContentType(), d.getSizeBytes(), d.getCreatedAt(), d.getReviewedAt());
    }
}
