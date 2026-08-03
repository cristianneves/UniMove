package com.unimove.domain.verification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credenciais da WhatsApp Cloud API. Todas vazias por padrao — so sao exigidas
 * quando {@code app.phone-verification.channel=WHATSAPP}, e nesse caso a
 * ausencia derruba o startup (ver {@code WhatsAppLinkChannel}), no mesmo molde
 * do {@code JwtService.assertSecretSafeInProd()}.
 *
 * @param businessNumber      numero da UniMove em E.164 sem '+' (ex.: 5574999998888).
 * @param appSecret           segredo do app Meta, usado no HMAC do webhook.
 * @param webhookVerifyToken  token do handshake GET que a Meta faz ao cadastrar o webhook.
 */
@ConfigurationProperties(prefix = "app.whatsapp")
public record WhatsAppProperties(
        @DefaultValue("") String businessNumber,
        @DefaultValue("") String appSecret,
        @DefaultValue("") String webhookVerifyToken
) {}
