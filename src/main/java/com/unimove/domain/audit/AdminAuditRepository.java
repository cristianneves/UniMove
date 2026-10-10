package com.unimove.domain.audit;

import com.unimove.domain.audit.dto.AuditEntryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface AdminAuditRepository extends JpaRepository<AdminAuditEntry, UUID> {

    @Query(
            value = """
                    SELECT new com.unimove.domain.audit.dto.AuditEntryResponse(
                        e.id, e.adminId, e.action, e.targetType, e.targetId, e.details, e.createdAt
                    )
                    FROM AdminAuditEntry e
                    WHERE (:action IS NULL OR e.action = :action)
                      AND (:targetId IS NULL OR e.targetId = :targetId)
                    ORDER BY e.createdAt DESC
                    """,
            countQuery = """
                    SELECT count(e) FROM AdminAuditEntry e
                    WHERE (:action IS NULL OR e.action = :action)
                      AND (:targetId IS NULL OR e.targetId = :targetId)
                    """
    )
    Page<AuditEntryResponse> search(@Param("action") AuditAction action,
                                    @Param("targetId") String targetId,
                                    Pageable pageable);
}
