package com.unimove.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Troca de telefone: o numero novo vem do desafio do WhatsApp verificado
 * (mesmo fluxo do cadastro), nunca de um campo digitado.
 */
public record ChangePhoneRequest(
        @NotBlank @Size(max = 64) String verificationToken
) {}
