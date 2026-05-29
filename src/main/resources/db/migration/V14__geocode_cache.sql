-- ============================================================================
-- V14: Cache de reverse geocoding (pin no mapa → endereco)
-- ============================================================================
-- Mesma filosofia do route_cache (regra 11): arrastar o pin gera muitas coordenadas
-- proximas; arredondando lat/lng a 4 casas (~11m) o reuso e alto. Hit no cache evita
-- bater no Photon (fair-use da instancia publica). Sem TTL no MVP — TRUNCATE se preciso.
-- Forward (autocomplete) NAO e cacheado: queries parciais tem baixa taxa de hit; o app
-- protege o provedor com debounce (~300ms). Ver docs/plano-busca-endereco.md.
-- ----------------------------------------------------------------------------
CREATE TABLE geocode_cache (
    id           BIGSERIAL      PRIMARY KEY,
    coord_hash   VARCHAR(64)    NOT NULL UNIQUE,
    display_name TEXT           NOT NULL,
    street       TEXT,
    city         TEXT,
    state        TEXT,
    lat          NUMERIC(10, 7) NOT NULL,
    lng          NUMERIC(10, 7) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
