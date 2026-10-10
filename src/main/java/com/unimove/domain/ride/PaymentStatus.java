package com.unimove.domain.ride;

/**
 * Situacao do recebimento da corrida. Nao existe "pago" para Pix: sem PSP o
 * payload e simulado e a corrida fica {@link #SIMULATED} — nunca vira receita
 * confirmada.
 */
public enum PaymentStatus {
    /** Dinheiro: aguardando o motorista registrar o recebimento. */
    PENDING,
    /** Dinheiro: motorista confirmou ter recebido ao menos o valor da corrida. */
    CONFIRMED,
    /** Dinheiro: recebido menos que o valor (ou nada) — vai para conciliacao. */
    DIVERGENT,
    /** Pix sem PSP: payload ficticio, nenhum dinheiro circulou pela plataforma. */
    SIMULATED
}
