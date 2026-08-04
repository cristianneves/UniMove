package com.unimove.domain.verification.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @param challengeId id para consultar o status (polling).
 * @param code        codigo do desafio — exibido pelo app caso o usuario prefira digitar.
 * @param waLink      link que o app abre para o usuario enviar a mensagem.
 * @param expiresAt   prazo para enviar a mensagem.
 */
public record ChallengeResponse(
        UUID challengeId,
        String code,
        String waLink,
        Instant expiresAt
) {}
