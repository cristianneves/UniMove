package com.unimove.domain.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Confirmacao da exclusao. Conta com senha precisa informa-la (um token
 * vazado nao basta para apagar a conta); conta so-social nao tem senha e
 * envia o corpo vazio.
 */
public record DeleteAccountRequest(
        @Size(max = 72) String password
) {}
