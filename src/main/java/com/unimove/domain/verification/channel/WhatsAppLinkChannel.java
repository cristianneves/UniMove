package com.unimove.domain.verification.channel;

import com.unimove.domain.verification.WhatsAppProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Monta o link wa.me com o codigo pre-preenchido. Um toque do usuario em
 * "enviar" e a Meta entrega a mensagem no nosso webhook junto com o wa_id dele.
 */
public class WhatsAppLinkChannel implements PhoneVerificationChannel {

    private final String businessNumber;

    public WhatsAppLinkChannel(WhatsAppProperties props) {
        // Mesma postura de JwtService.assertSecretSafeInProd(): configuracao
        // faltando derruba o startup em vez de falhar silenciosamente em runtime.
        if (props.businessNumber() == null || props.businessNumber().isBlank()) {
            throw new IllegalStateException(
                    "app.whatsapp.business-number e obrigatorio quando "
                            + "app.phone-verification.channel=WHATSAPP (env WHATSAPP_BUSINESS_NUMBER).");
        }
        if (props.appSecret() == null || props.appSecret().isBlank()) {
            throw new IllegalStateException(
                    "app.whatsapp.app-secret e obrigatorio quando "
                            + "app.phone-verification.channel=WHATSAPP (env WHATSAPP_APP_SECRET) — "
                            + "sem ele o webhook nao consegue validar a assinatura da Meta.");
        }
        this.businessNumber = props.businessNumber().trim();
    }

    @Override
    public String buildChallengeLink(String code) {
        String text = URLEncoder.encode(ChallengeMessage.forCode(code), StandardCharsets.UTF_8);
        return "https://wa.me/" + businessNumber + "?text=" + text;
    }
}
