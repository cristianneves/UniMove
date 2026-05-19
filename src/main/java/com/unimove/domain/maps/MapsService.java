package com.unimove.domain.maps;

public interface MapsService {

    RouteInfo route(double latOrigem, double lngOrigem, double latDestino, double lngDestino);
}
