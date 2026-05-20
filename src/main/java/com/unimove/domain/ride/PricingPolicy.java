package com.unimove.domain.ride;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PricingPolicy {

    private static final BigDecimal BASE = new BigDecimal("5.50");
    private static final BigDecimal PER_KM = new BigDecimal("2.10");
    private static final BigDecimal PER_MIN = new BigDecimal("0.20");

    public BigDecimal calculate(BigDecimal distanciaKm, int tempoMin) {
        BigDecimal byDistance = PER_KM.multiply(distanciaKm);
        BigDecimal byTime = PER_MIN.multiply(BigDecimal.valueOf(tempoMin));
        return BASE.add(byDistance).add(byTime).setScale(2, RoundingMode.HALF_UP);
    }
}
