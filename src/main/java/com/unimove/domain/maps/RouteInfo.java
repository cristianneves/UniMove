package com.unimove.domain.maps;

import java.math.BigDecimal;

/**
 * Resultado de uma rota do OSRM. {@code geometry} e a polyline codificada
 * (Encoded Polyline Algorithm, precisao 5) do trajeto completo origem→...→destino,
 * usada pelo front para desenhar a linha da rota no mapa. Pode ser {@code null}
 * para hits antigos do route_cache anteriores a esta feature.
 */
public record RouteInfo(BigDecimal distanciaKm, int tempoMin, String geometry) {}
