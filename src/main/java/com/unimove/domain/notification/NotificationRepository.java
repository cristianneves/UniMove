package com.unimove.domain.notification;

import com.unimove.domain.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query(
            value = """
                    SELECT new com.unimove.domain.notification.dto.NotificationResponse(
                        n.id, n.kind, n.title, n.body, n.createdAt, n.readAt
                    )
                    FROM Notification n WHERE n.userId = :userId
                    ORDER BY n.createdAt DESC
                    """,
            countQuery = "SELECT count(n) FROM Notification n WHERE n.userId = :userId"
    )
    Page<NotificationResponse> findInbox(@Param("userId") UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    int deleteByUser(@Param("userId") UUID userId);
}
