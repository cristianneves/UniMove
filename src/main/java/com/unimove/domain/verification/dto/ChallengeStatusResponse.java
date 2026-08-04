package com.unimove.domain.verification.dto;

import com.unimove.domain.verification.PhoneVerificationStatus;
import com.unimove.domain.verification.RejectionReason;

/**
 * Resposta do polling.
 *
 * @param status            estado atual do desafio.
 * @param phone             telefone confirmado, mascarado — so para o usuario conferir na tela.
 * @param verificationToken credencial para o /auth/register. So vem em VERIFIED.
 * @param rejectionReason   preenchido em REJECTED para o app dar a mensagem certa.
 */
public record ChallengeStatusResponse(
        PhoneVerificationStatus status,
        String phone,
        String verificationToken,
        RejectionReason rejectionReason
) {}
