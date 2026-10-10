package com.unimove.domain.user.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "driver_documents")
@Getter
@Setter
@NoArgsConstructor
public class DriverDocument {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "driver_id", nullable = false, updatable = false)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10, updatable = false)
    private DriverDocumentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DriverDocumentStatus status;

    /** Caminho no bucket privado; nulo depois do expurgo do arquivo. */
    @Column(name = "storage_path", length = 255)
    private String storagePath;

    @Column(name = "content_type", nullable = false, length = 50, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private Integer sizeBytes;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reviewed_by_admin_id")
    private UUID reviewedByAdminId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
