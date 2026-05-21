package com.unimove.domain.ride;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RideRatingRepository extends JpaRepository<RideRating, UUID> {

    boolean existsByRideIdAndRaterId(UUID rideId, UUID raterId);
}
