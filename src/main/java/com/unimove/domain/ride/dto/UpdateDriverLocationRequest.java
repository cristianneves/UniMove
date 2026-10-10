package com.unimove.domain.ride.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param recordedAt quando o GPS do aparelho fixou a posicao. Opcional por
 *                   compatibilidade; sem ele o servidor assume "agora" e nao
 *                   consegue rejeitar posicao velha que chegou atrasada.
 * @param accuracyM  raio de precisao reportado pelo GPS, em metros. Opcional.
 */
public record UpdateDriverLocationRequest(
        @NotNull
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        BigDecimal lat,

        @NotNull
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        BigDecimal lng,

        Instant recordedAt,

        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100000.0")
        BigDecimal accuracyM
) {
    public UpdateDriverLocationRequest(BigDecimal lat, BigDecimal lng) {
        this(lat, lng, null, null);
    }
}
