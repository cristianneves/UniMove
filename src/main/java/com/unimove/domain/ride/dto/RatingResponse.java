package com.unimove.domain.ride.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RatingResponse(
        UUID id,
        UUID rideId,
        UUID raterId,
        UUID rateeId,
        int score,
        String comment,
        Instant createdAt,
        BigDecimal rateeNewRatingAvg,
        int rateeNewRatingCount
) {}
