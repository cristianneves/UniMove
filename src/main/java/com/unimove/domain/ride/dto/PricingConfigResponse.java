package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.PricingConfig;
import com.unimove.domain.ride.RideCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PricingConfigResponse(
        UUID id,
        String cidade,
        RideCategory category,
        BigDecimal base,
        BigDecimal perKm,
        BigDecimal perMin,
        Instant updatedAt,
        UUID updatedByAdminId
) {
    public static PricingConfigResponse from(PricingConfig c) {
        return new PricingConfigResponse(
                c.getId(),
                c.getCidade(),
                c.getCategory(),
                c.getBase(),
                c.getPerKm(),
                c.getPerMin(),
                c.getUpdatedAt(),
                c.getUpdatedByAdminId()
        );
    }
}
