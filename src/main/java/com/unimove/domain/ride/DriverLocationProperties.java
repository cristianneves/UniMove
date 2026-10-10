package com.unimove.domain.ride;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Limites de sanidade da posicao do motorista durante a corrida.
 *
 * @param maxAgeSeconds        posicao fixada ha mais que isso e descartada como velha.
 * @param maxFutureSkewSeconds tolerancia de relogio do aparelho adiantado.
 * @param maxAccuracyM         precisao pior que isso (raio maior) nao serve para tracking.
 * @param maxSpeedKmh          velocidade implicita acima disso entre duas posicoes e salto impossivel.
 * @param minJumpKm            abaixo dessa distancia nao se avalia velocidade (ruido de GPS com dt pequeno).
 */
@Validated
@ConfigurationProperties(prefix = "app.ride.location")
public record DriverLocationProperties(
        @DefaultValue("120") @Positive long maxAgeSeconds,
        @DefaultValue("30") @Positive long maxFutureSkewSeconds,
        @DefaultValue("100") @Positive double maxAccuracyM,
        @DefaultValue("180") @Positive double maxSpeedKmh,
        @DefaultValue("0.5") @Positive double minJumpKm
) {
    public static DriverLocationProperties defaults() {
        return new DriverLocationProperties(120, 30, 100, 180, 0.5);
    }
}
