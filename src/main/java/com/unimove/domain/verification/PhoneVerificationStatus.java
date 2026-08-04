package com.unimove.domain.verification;

public enum PhoneVerificationStatus {

    /** Desafio criado, aguardando a mensagem do usuario no WhatsApp. */
    PENDING,

    /** Mensagem recebida: o telefone do remetente esta confirmado e o token emitido. */
    VERIFIED,

    /** Token ja trocado por uma conta no /auth/register. Nao serve mais. */
    CONSUMED,

    /** Recusado por regra de negocio (ex.: telefone ja pertence a outra conta). */
    REJECTED,

    /** Venceu antes de o usuario enviar a mensagem. */
    EXPIRED
}
