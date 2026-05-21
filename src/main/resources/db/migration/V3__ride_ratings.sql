-- ============================================================================
-- V3: Rating bidirecional pos-corrida (ver diretriz 12 do CLAUDE.md)
-- ============================================================================
-- - ride_ratings: 1 rating por direcao por ride (UNIQUE(ride_id, rater_id))
-- - users.rating_avg / rating_count: denormalizacao para nao fazer AVG nos polls
-- ============================================================================

ALTER TABLE users
    ADD COLUMN rating_avg   NUMERIC(3, 2) NOT NULL DEFAULT 0,
    ADD COLUMN rating_count INTEGER       NOT NULL DEFAULT 0;

CREATE TABLE ride_ratings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id     UUID         NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
    rater_id    UUID         NOT NULL REFERENCES users(id),
    ratee_id    UUID         NOT NULL REFERENCES users(id),
    score       SMALLINT     NOT NULL,
    comment     VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ride_ratings_score CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT uq_ride_ratings_ride_rater UNIQUE (ride_id, rater_id)
);

CREATE INDEX idx_ride_ratings_ratee ON ride_ratings (ratee_id);
