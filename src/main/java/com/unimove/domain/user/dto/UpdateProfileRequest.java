package com.unimove.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Campos editáveis do perfil. E-mail (login) e role são imutáveis no MVP.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 20) String phone,
        @NotBlank @Size(max = 80) String cidade
) {
}
