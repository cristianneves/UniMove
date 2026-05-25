package com.unimove.domain.maps;

/**
 * Ponto geografico (lat/lng) usado para descrever uma rota com N waypoints.
 * Ordem importa: o primeiro e a origem, o ultimo e o destino, os intermediarios
 * sao as paradas na ordem de visita.
 */
public record GeoPoint(double lat, double lng) {}
