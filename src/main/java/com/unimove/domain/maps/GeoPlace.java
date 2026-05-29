package com.unimove.domain.maps;

import java.math.BigDecimal;

/**
 * Resultado de geocoding (busca por texto ou reverse do pin). {@code displayName}
 * e o rotulo amigavel pronto pra UI (ex: "Av. Brasil, 1200 — Centro"); os campos
 * estruturados (street/city/state) ficam disponiveis caso o app queira formatar
 * diferente. lat/lng e o que alimenta origem/destino/paradas da corrida.
 */
public record GeoPlace(
        String displayName,
        BigDecimal lat,
        BigDecimal lng,
        String street,
        String city,
        String state
) {}
