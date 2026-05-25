package com.unimove.domain.maps;

import java.util.List;

public interface MapsService {

    RouteInfo route(double latOrigem, double lngOrigem, double latDestino, double lngDestino);

    /**
     * Rota que passa por todos os waypoints na ordem dada (origem, ...paradas, destino).
     * Exige ao menos 2 pontos. Distancia e tempo retornados ja incluem o desvio das paradas.
     */
    RouteInfo route(List<GeoPoint> waypoints);
}
