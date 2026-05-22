package com.unimove.domain.ride;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PricingPolicy {

    private static final BigDecimal BASE = new BigDecimal("5.50");
    private static final BigDecimal PER_KM = new BigDecimal("2.10");
    private static final BigDecimal PER_MIN = new BigDecimal("0.20");

    private static final BigDecimal MULT_MOTO = new BigDecimal("0.7");
    private static final BigDecimal MULT_CARRO = BigDecimal.ONE;

    public BigDecimal calculate(BigDecimal distanciaKm, int tempoMin) {
        return calculate(distanciaKm, tempoMin, RideCategory.CARRO);
    }

    public BigDecimal calculate(BigDecimal distanciaKm, int tempoMin, RideCategory category) {
        BigDecimal byDistance = PER_KM.multiply(distanciaKm);
        BigDecimal byTime = PER_MIN.multiply(BigDecimal.valueOf(tempoMin));
        BigDecimal raw = BASE.add(byDistance).add(byTime);
        return raw.multiply(multiplierFor(category)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal multiplierFor(RideCategory category) {
        return switch (category) {
            case MOTO -> MULT_MOTO;
            case CARRO -> MULT_CARRO;
        };
    }
}
