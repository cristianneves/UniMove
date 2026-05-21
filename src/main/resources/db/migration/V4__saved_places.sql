-- ============================================================================
-- V4: Enderecos favoritos do passageiro (Casa, Trabalho, etc.)
-- ============================================================================
-- Reduz friccao na criacao da corrida — o app pre-popula origem/destino a partir
-- de lugares salvos. UNIQUE(user_id, label) garante que o mesmo usuario nao tenha
-- dois "Casa" diferentes; reusar o label sobrescreve via DELETE + POST.
-- ============================================================================

CREATE TABLE saved_places (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label       VARCHAR(40)  NOT NULL,
    address     VARCHAR(200) NOT NULL,
    lat         NUMERIC(10, 7) NOT NULL,
    lng         NUMERIC(10, 7) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_saved_places_user_label UNIQUE (user_id, label),
    CONSTRAINT chk_saved_places_lat  CHECK (lat  BETWEEN -90  AND 90),
    CONSTRAINT chk_saved_places_lng  CHECK (lng BETWEEN -180 AND 180)
);

CREATE INDEX idx_saved_places_user_id ON saved_places (user_id);
