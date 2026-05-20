package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.PaymentMethod;
import com.unimove.domain.ride.RideStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminRideItem(
        UUID id,
        String cidade,
        RideStatus status,
        PaymentMethod paymentMethod,
        BigDecimal preco,
        UUID passageiroId,
        UUID motoristaId,
        Instant createdAt,
        Instant acceptedAt,
        Instant completedAt,
        Instant cancelledAt
) {}
