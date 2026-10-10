package com.unimove.domain.user.document;

import com.unimove.domain.user.document.dto.PendingDocumentItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, UUID> {

    /** Historico completo do motorista, mais recente primeiro (o primeiro de cada tipo e o vigente). */
    List<DriverDocument> findByDriverIdOrderByCreatedAtDesc(UUID driverId);

    Optional<DriverDocument> findFirstByDriverIdAndTypeOrderByCreatedAtDesc(UUID driverId, DriverDocumentType type);

    Optional<DriverDocument> findByIdAndDriverId(UUID id, UUID driverId);

    /** Fila de analise (usa idx_driver_documents_pending). */
    @Query(
            value = """
                    SELECT new com.unimove.domain.user.document.dto.PendingDocumentItem(
                        d.id, d.driverId, u.name, u.cidade, d.type, d.createdAt
                    )
                    FROM DriverDocument d, User u
                    WHERE u.id = d.driverId
                      AND d.status = com.unimove.domain.user.document.DriverDocumentStatus.SUBMITTED
                      AND u.status <> com.unimove.domain.user.UserStatus.DELETED
                    ORDER BY d.createdAt ASC
                    """,
            countQuery = """
                    SELECT count(d) FROM DriverDocument d, User u
                    WHERE u.id = d.driverId
                      AND d.status = com.unimove.domain.user.document.DriverDocumentStatus.SUBMITTED
                      AND u.status <> com.unimove.domain.user.UserStatus.DELETED
                    """
    )
    Page<PendingDocumentItem> findPendingReview(Pageable pageable);

    @Query("SELECT d.storagePath FROM DriverDocument d WHERE d.driverId = :driverId AND d.storagePath IS NOT NULL")
    List<String> findStoragePaths(@Param("driverId") UUID driverId);
}
