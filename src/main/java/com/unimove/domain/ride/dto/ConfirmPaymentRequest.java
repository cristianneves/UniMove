package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record ConfirmPaymentRequest(
        @NotNull PaymentMethod method
) {}
