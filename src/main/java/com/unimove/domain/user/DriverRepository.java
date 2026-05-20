package com.unimove.domain.user;

import com.unimove.domain.user.dto.PendingDriverItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    @Modifying
    @Query("UPDATE Driver d SET d.lastSeenAt = :now WHERE d.user.id = :userId")
    int updateLastSeenAt(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
            SELECT new com.unimove.domain.user.dto.PendingDriverItem(
                d.user.id, d.user.name, d.user.email, d.user.phone, d.user.cidade,
                d.vehicleType, d.vehiclePlate, d.user.createdAt
            )
            FROM Driver d
            WHERE d.approved = false
            ORDER BY d.user.createdAt ASC
            """)
    List<PendingDriverItem> findPending();
}
