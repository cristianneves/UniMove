package com.unimove.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    @Modifying
    @Query("UPDATE Driver d SET d.lastSeenAt = :now WHERE d.user.id = :userId")
    int updateLastSeenAt(@Param("userId") UUID userId, @Param("now") Instant now);
}
