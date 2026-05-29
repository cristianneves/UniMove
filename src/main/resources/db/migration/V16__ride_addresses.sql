-- ============================================================================
-- V16: Texto do endereco de origem/destino da corrida
-- ============================================================================
-- Ate aqui a corrida guardava SO coordenadas (lat/lng). O app resolve o texto
-- do endereco via geocoding (regra 20) ao montar a corrida — passamos a persistir
-- esse texto para alimentar a lista "destinos recentes" (estilo Uber) sem precisar
-- de reverse-geocoding posterior. Colunas nullable: corridas antigas e requests
-- sem endereco ficam nulos (o front cai no fallback de reverse-geocoding).
--
-- Indice parcial sustenta GET /rides/recent-destinations: filtra pelo passageiro,
-- ordena por recencia e ignora corridas sem texto de endereco.
-- ============================================================================

ALTER TABLE rides ADD COLUMN origem_endereco  VARCHAR(200);
ALTER TABLE rides ADD COLUMN destino_endereco VARCHAR(200);

CREATE INDEX idx_rides_passageiro_recent
    ON rides (passageiro_id, created_at DESC)
    WHERE destino_endereco IS NOT NULL;
