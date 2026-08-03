package com.unimove.domain.verification.channel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formato da mensagem que o usuario envia. Fica isolado aqui porque o link
 * (canal) e o webhook precisam concordar sobre ele: um monta, o outro extrai.
 */
public final class ChallengeMessage {

    private static final String PREFIX = "UNIMOVE-";

    /** Codigo em maiusculas: o texto recebido e normalizado antes de casar. */
    private static final Pattern CODE_PATTERN = Pattern.compile(PREFIX + "([A-Z0-9]{8})");

    private ChallengeMessage() {
    }

    public static String forCode(String code) {
        return PREFIX + code;
    }

    /**
     * @return o codigo contido na mensagem, ou {@code null} se for uma mensagem
     *         qualquer — gente escreve no numero do suporte, e isso nao e erro.
     */
    public static String extractCode(String messageText) {
        if (messageText == null) {
            return null;
        }
        Matcher matcher = CODE_PATTERN.matcher(messageText.toUpperCase());
        return matcher.find() ? matcher.group(1) : null;
    }
}
