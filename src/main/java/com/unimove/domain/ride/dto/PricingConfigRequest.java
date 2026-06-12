package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.RideCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PricingConfigRequest(
        @NotBlank @Size(max = 80)
        String cidade,

        @NotNull
        RideCategory category,

        @NotNull @DecimalMin(value = "0.00", inclusive = true)
        @Digits(integer = 8, fraction = 2)
        BigDecimal base,

        @NotNull @DecimalMin(value = "0.00", inclusive = true)
        @Digits(integer = 8, fraction = 2)
        BigDecimal perKm,

        @NotNull @DecimalMin(value = "0.00", inclusive = true)
        @Digits(integer = 8, fraction = 2)
        BigDecimal perMin,

        /** Liga o surge para esta cidade+categoria. Null no request = desligado. */
        Boolean surgeEnabled,

        /** Teto do multiplicador de surge (1.00..3.00). Null = default 1.50 (no service). */
        @DecimalMin(value = "1.00", inclusive = true)
        @DecimalMax(value = "3.00", inclusive = true)
        @Digits(integer = 1, fraction = 2)
        BigDecimal surgeCap
) {}
