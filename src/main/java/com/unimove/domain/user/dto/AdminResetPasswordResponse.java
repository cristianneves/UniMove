package com.unimove.domain.user.dto;

import java.util.UUID;

/**
 * Resposta do reset de senha pelo admin. A senha temporária é exibida uma única
 * vez — o admin repassa ao usuário por canal externo (WhatsApp/telefone) e o
 * usuário troca via PUT /users/me/password.
 */
public record AdminResetPasswordResponse(
        UUID userId,
        String email,
        String temporaryPassword
) {
}
