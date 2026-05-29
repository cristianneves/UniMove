package com.unimove.domain.maps;

import java.util.List;

/**
 * Ponte texto ↔ coordenada (geocoding), via Photon (OSM). Irmao do {@link MapsService}
 * (rotas), mas com provedor e base URL proprios. Ver docs/plano-busca-endereco.md.
 */
public interface GeocodingService {

    /**
     * Busca por texto (autocomplete). {@code biasLat}/{@code biasLng} sao opcionais
     * (null = sem vies) e enviesam os resultados pra perto do ponto — o app passa o
     * centro do mapa ou o GPS do usuario, igual Uber/99.
     */
    List<GeoPlace> search(String query, Double biasLat, Double biasLng, int limit);

    /** Reverse: dado um ponto (pin arrastado no mapa), retorna o endereco mais proximo. */
    GeoPlace reverse(double lat, double lng);
}
