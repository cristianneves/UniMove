package com.unimove.domain.verification;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Parametros da verificacao de telefone.
 *
 * @param channel                canal ativo: {@code LOG} (dev/testes, imprime o link) ou {@code WHATSAPP}.
 * @param challengeTtlMinutes    prazo para o usuario enviar a mensagem no WhatsApp.
 * @param tokenTtlMinutes        prazo para trocar o token verificado por uma conta.
 * @param maxChallengesPerIpHour teto de desafios por IP por hora.
 * @param purgeIntervalMs        intervalo (ms) entre execucoes do expirador/purgador.
 */
@Validated
@ConfigurationProperties(prefix = "app.phone-verification")
public record PhoneVerificationProperties(
        @DefaultValue("LOG") Channel channel,
        @DefaultValue("10") @Positive int challengeTtlMinutes,
        @DefaultValue("15") @Positive int tokenTtlMinutes,
        @DefaultValue("20") @Positive int maxChallengesPerIpHour,
        @DefaultValue("600000") @Positive long purgeIntervalMs
) {
    public enum Channel { LOG, WHATSAPP }
}
