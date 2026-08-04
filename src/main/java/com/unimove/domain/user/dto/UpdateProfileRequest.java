package com.unimove.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Campos editáveis do perfil. E-mail (login) e role são imutáveis no MVP.
 *
 * O telefone também é imutável: ele foi verificado no WhatsApp durante o
 * cadastro, e deixar trocá-lo livremente aqui tornaria aquela verificação
 * decorativa. Troca de número passa pelo admin, mesmo caminho do reset de senha.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 80) String cidade
) {
}
