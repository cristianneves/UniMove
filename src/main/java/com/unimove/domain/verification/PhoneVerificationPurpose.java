package com.unimove.domain.verification;

/**
 * Para que serve o desafio. Decide a regra do webhook sobre o numero que
 * respondeu e qual fluxo pode consumir o token — um token de recuperacao
 * nunca cria conta, e um de cadastro nunca troca senha.
 */
public enum PhoneVerificationPurpose {

    /** Cadastro ou troca de telefone: o numero NAO pode ter conta. */
    NEW_PHONE,

    /** Esqueci minha senha: o numero PRECISA ter conta. */
    PASSWORD_RESET
}
