package com.unimove.domain.user.document;

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

    @Query("SELECT d.storagePath FROM DriverDocument d WHERE d.driverId = :driverId AND d.storagePath IS NOT NULL")
    List<String> findStoragePaths(@Param("driverId") UUID driverId);
}
