package com.unimove.domain.verification.channel;

/**
 * Como o usuario deve devolver o codigo do desafio.
 *
 * O nome "channel" nao significa que a UniMove envia mensagem: no fluxo reverso
 * quem envia e o usuario. O canal apenas monta o destino — e e exatamente isso
 * que mantem o custo em zero na Cloud API, ja que mensagem recebida nao e
 * cobrada e so template enviado pela empresa e.
 */
public interface PhoneVerificationChannel {

    /**
     * @param code codigo do desafio.
     * @return link que o app abre para o usuario enviar a mensagem.
     */
    String buildChallengeLink(String code);
}
