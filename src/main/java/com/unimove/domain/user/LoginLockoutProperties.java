package com.unimove.domain.user;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Parametros da protecao contra brute-force no login.
 *
 * @param enabled           liga/desliga o bloqueio (util para testes/manutencao).
 * @param maxAttempts       falhas consecutivas permitidas antes do bloqueio.
 * @param lockoutMinutes    duracao (min) do bloqueio e da janela de contagem de falhas.
 * @param cleanupIntervalMs intervalo (ms) entre limpezas das entradas expiradas.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth.lockout")
public record LoginLockoutProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5") @Positive int maxAttempts,
        @DefaultValue("15") @Positive int lockoutMinutes,
        @DefaultValue("600000") @Positive long cleanupIntervalMs
) {}
