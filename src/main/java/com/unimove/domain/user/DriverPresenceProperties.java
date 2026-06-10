package com.unimove.domain.user;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Parametros do job que marca offline motoristas inativos.
 *
 * @param enabled             liga/desliga o job (util para testes/manutencao).
 * @param offlineAfterMinutes inatividade maxima (min) antes de marcar offline.
 * @param scanIntervalMs      intervalo (ms) entre varreduras do job.
 */
@Validated
@ConfigurationProperties(prefix = "app.driver.presence")
public record DriverPresenceProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") @Positive int offlineAfterMinutes,
        @DefaultValue("60000") @Positive long scanIntervalMs
) {}
