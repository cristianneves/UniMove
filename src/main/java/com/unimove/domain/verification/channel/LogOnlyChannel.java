package com.unimove.domain.verification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Canal de desenvolvimento e testes: nao existe numero de WhatsApp configurado,
 * entao o link aponta para um numero de exemplo e o codigo sai no log.
 *
 * O fluxo continua sendo o real — para concluir a verificacao basta chamar
 * {@code POST /webhooks/whatsapp} com o payload da Cloud API (ver docs/api.http),
 * o mesmo caminho que a Meta usa em producao.
 */
public class LogOnlyChannel implements PhoneVerificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LogOnlyChannel.class);
    private static final String PLACEHOLDER_NUMBER = "5500000000000";

    @Override
    public String buildChallengeLink(String code) {
        log.info("[phone-verification][LOG] Desafio criado. Mensagem esperada: \"{}\". "
                        + "Simule a Meta com POST /webhooks/whatsapp.",
                ChallengeMessage.forCode(code));
        return "https://wa.me/" + PLACEHOLDER_NUMBER + "?text="
                + ChallengeMessage.forCode(code).replace(' ', '+');
    }
}
