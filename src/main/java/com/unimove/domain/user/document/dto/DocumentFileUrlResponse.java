package com.unimove.domain.user.document.dto;

import java.time.Instant;

/** URL assinada de vida curta para o admin visualizar o arquivo. */
public record DocumentFileUrlResponse(String url, Instant expiresAt) {}
