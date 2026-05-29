package com.unimove.domain.ride.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Destino recente do passageiro para a tela "para onde vamos?" (estilo Uber).
 * Derivado do historico de corridas (item: enderecos recentes) — endereco textual
 * + coordenadas prontas para pre-popular o destino sem nova chamada de geocoding.
 */
public record RecentDestinationResponse(
        String address,
        BigDecimal lat,
        BigDecimal lng,
        Instant lastUsedAt
) {}
