package com.unimove.domain.ride;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Parametros do job que expira corridas paradas no mural.
 *
 * @param enabled        liga/desliga o job (util para testes/manutencao).
 * @param ttlMinutes     tempo maximo (min) que uma corrida pode ficar em AVAILABLE_IN_MURAL.
 * @param scanIntervalMs intervalo (ms) entre varreduras do job.
 */
@Validated
@ConfigurationProperties(prefix = "app.ride.expiration")
public record RideExpirationProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("15") @Positive int ttlMinutes,
        @DefaultValue("60000") @Positive long scanIntervalMs
) {}
