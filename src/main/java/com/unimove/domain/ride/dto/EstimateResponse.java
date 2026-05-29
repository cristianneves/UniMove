package com.unimove.domain.ride.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resposta do POST /rides/estimate. A rota (distancia/tempo/geometria) e unica
 * — vem de UMA chamada ao OSRM — e {@code options} traz o preco da MESMA rota
 * em cada categoria de veiculo, alimentando a tela "escolha sua corrida"
 * (estilo Uber). {@code geometry} e a polyline codificada (precisao 5) do
 * trajeto, para o front desenhar a rota na confirmacao. Chamado uma unica vez
 * (nao e polling).
 *
 * {@code preco} e mantido por compatibilidade: e o preco da categoria pedida no
 * request (ou CARRO por default). Clientes novos devem usar {@code options}.
 */
public record EstimateResponse(
        BigDecimal distanciaKm,
        Integer tempoMin,
        BigDecimal preco,
        String geometry,
        List<CategoryOption> options
) {}
