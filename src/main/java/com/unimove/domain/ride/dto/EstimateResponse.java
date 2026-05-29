package com.unimove.domain.ride.dto;

import java.math.BigDecimal;

/**
 * Resposta do POST /rides/estimate. {@code geometry} e a polyline codificada
 * (precisao 5) do trajeto, para o front desenhar a rota na tela de confirmacao
 * antes de criar a corrida. Chamado uma unica vez (nao e polling).
 */
public record EstimateResponse(
        BigDecimal distanciaKm,
        Integer tempoMin,
        BigDecimal preco,
        String geometry
) {}
