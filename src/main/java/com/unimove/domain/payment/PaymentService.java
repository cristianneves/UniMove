package com.unimove.domain.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentService {

    String generatePixPayload(UUID rideId, BigDecimal valor);
}
