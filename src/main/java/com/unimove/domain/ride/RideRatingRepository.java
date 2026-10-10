package com.unimove.domain.ride;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface RideRatingRepository extends JpaRepository<RideRating, UUID> {

    boolean existsByRideIdAndRaterId(UUID rideId, UUID raterId);

    @Modifying
    @Query("UPDATE RideRating r SET r.comment = NULL WHERE r.raterId = :raterId AND r.comment IS NOT NULL")
    int clearCommentsWrittenBy(@Param("raterId") UUID raterId);

    @Modifying
    @Query("UPDATE RideRating r SET r.comment = NULL WHERE r.createdAt < :cutoff AND r.comment IS NOT NULL")
    int clearCommentsCreatedBefore(@Param("cutoff") Instant cutoff);
}
