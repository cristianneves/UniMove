package com.unimove.domain.verification;

/**
 * Motivo de um desafio ter sido recusado. Vai para o app para que ele mostre
 * uma mensagem util em vez de um erro generico.
 */
public enum RejectionReason {

    /** O numero que enviou a mensagem ja tem conta na UniMove — o caminho e login. */
    PHONE_IN_USE,

    /** Recuperacao de senha de um numero sem conta na UniMove — o caminho e cadastro. */
    PHONE_NOT_REGISTERED
}
