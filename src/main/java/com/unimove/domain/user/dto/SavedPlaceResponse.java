package com.unimove.domain.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SavedPlaceResponse(
        UUID id,
        String label,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        Instant createdAt
) {}
