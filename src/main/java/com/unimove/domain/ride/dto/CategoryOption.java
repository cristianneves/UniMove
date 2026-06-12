package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.RideCategory;

import java.math.BigDecimal;

/**
 * Uma opcao de corrida na tela "escolha sua corrida" (estilo Uber): a mesma
 * rota precificada para cada categoria de veiculo. A rota (distancia/tempo/
 * geometria) e identica entre opcoes — so o preco varia conforme os
 * coeficientes da {@code PricingPolicy} (regra 2/15) e o surge da categoria.
 * Exposta em lista no {@link EstimateResponse}. {@code preco} ja inclui o surge;
 * {@code surgeMultiplier} (1.00 = sem surge) e exposto para a UI exibir "1.3x".
 */
public record CategoryOption(
        RideCategory category,
        BigDecimal preco,
        BigDecimal surgeMultiplier
) {}
