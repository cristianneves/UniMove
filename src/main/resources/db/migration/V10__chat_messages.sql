-- ============================================================================
-- V10: Chat in-app entre passageiro e motorista durante a corrida
-- ============================================================================
-- Persistencia obrigatoria (disputas/moderacao). Transporte em runtime e
-- SSE (Server-Sent Events) + POST. A coluna seq (BIGSERIAL) e usada como
-- Last-Event-Id pra cliente retomar de onde parou ao reconectar.
--
-- Chat so esta habilitado nos estados DRIVER_EN_ROUTE e IN_PROGRESS
-- (regra aplicada em RideService.assertChatAllowed). Nao ha TTL nem
-- delecao no MVP.
-- ============================================================================

CREATE TABLE chat_messages (
    id           UUID PRIMARY KEY,
    seq          BIGSERIAL                NOT NULL UNIQUE,
    ride_id      UUID                     NOT NULL,
    sender_id    UUID                     NOT NULL,
    sender_role  VARCHAR(20)              NOT NULL,
    body         TEXT                     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT fk_chat_ride       FOREIGN KEY (ride_id) REFERENCES rides(id) ON DELETE CASCADE,
    CONSTRAINT chk_chat_role      CHECK (sender_role IN ('PASSAGEIRO','MOTORISTA')),
    CONSTRAINT chk_chat_body_len  CHECK (length(body) BETWEEN 1 AND 1000)
);

-- Indice principal: listagem em ordem de chegada + "since seq".
CREATE INDEX idx_chat_ride_seq ON chat_messages (ride_id, seq);
