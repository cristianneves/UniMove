package com.unimove.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Esqueci minha senha: token de um desafio {@code PASSWORD_RESET} verificado
 * no WhatsApp (prova a posse do numero da conta) + a senha nova.
 */
public record ResetPasswordRequest(
        @NotBlank String verificationToken,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {
}
