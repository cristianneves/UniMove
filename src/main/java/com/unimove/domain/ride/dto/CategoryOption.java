package com.unimove.domain.ride.dto;

import com.unimove.domain.ride.RideCategory;

import java.math.BigDecimal;

/**
 * Uma opcao de corrida na tela "escolha sua corrida" (estilo Uber): a mesma
 * rota precificada para cada categoria de veiculo. A rota (distancia/tempo/
 * geometria) e identica entre opcoes — so o preco varia conforme os
 * coeficientes da {@code PricingPolicy} (regra 2/15). Exposta em lista no
 * {@link EstimateResponse}.
 */
public record CategoryOption(
        RideCategory category,
        BigDecimal preco
) {}
