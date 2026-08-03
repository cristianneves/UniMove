package com.unimove.domain.verification;

/**
 * Porta que {@code domain.verification} declara e {@code domain.user} implementa.
 *
 * A direcao da dependencia e proposital: se a verificacao importasse o
 * repositorio de usuarios, os dois pacotes ficariam em ciclo (user tambem
 * depende de {@link PhoneVerificationService} no cadastro). Declarando a porta
 * aqui, o fluxo fica so user -> verification.
 */
public interface PhoneRegistry {

    /**
     * @param phoneE164 telefone no formato entregue pela Meta (ex.: {@code 5574999998888}).
     * @return true se algum usuario ja tem esse telefone.
     */
    boolean isPhoneRegistered(String phoneE164);
}
