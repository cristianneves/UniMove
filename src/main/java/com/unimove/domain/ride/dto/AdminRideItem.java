package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.PaymentMethod;
import com.unimove.domain.ride.RideCategory;
import com.unimove.domain.ride.RideStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminRideItem(
        UUID id,
        String cidade,
        RideStatus status,
        RideCategory category,
        PaymentMethod paymentMethod,
        BigDecimal preco,
        BigDecimal cancellationFee,
        UUID passageiroId,
        UUID motoristaId,
        Instant createdAt,
        Instant acceptedAt,
        Instant completedAt,
        Instant cancelledAt
) {}
