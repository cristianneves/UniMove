package com.unimove.domain.ride.dto;

/**
 * Resposta leve do GET /rides/{id}/route (regra 19). Devolve apenas a polyline
 * codificada (precisao 5) do trajeto origem→...→destino. A geometria e estatica
 * durante a corrida, entao o front busca UMA vez e desenha a linha no mapa — fora
 * do polling de 5s do GET /rides/{id} (que segue leve, regra 3). {@code geometry}
 * pode ser {@code null} em corridas antigas anteriores a esta feature.
 */
public record RideRouteResponse(String geometry) {}
