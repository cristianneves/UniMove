-- ============================================================================
-- V12: Expiracao da corrida no mural ("nenhum motorista")
-- ============================================================================
-- Uma corrida em AVAILABLE_IN_MURAL que nenhum motorista aceita dentro do TTL
-- (ver app.ride.expiration.ttl-minutes) e transicionada para o estado terminal
-- EXPIRED por um job @Scheduled. Estado distinto de CANCELLED para analytics e
-- UX ("nenhum motorista disponivel").
-- ----------------------------------------------------------------------------

-- Novo estado terminal no check constraint de status.
ALTER TABLE rides DROP CONSTRAINT chk_rides_status;
ALTER TABLE rides ADD CONSTRAINT chk_rides_status CHECK (status IN (
    'PENDING_PAYMENT',
    'AVAILABLE_IN_MURAL',
    'DRIVER_EN_ROUTE',
    'IN_PROGRESS',
    'COMPLETED',
    'CANCELLED',
    'EXPIRED'
));

-- Carimbo do momento da expiracao.
ALTER TABLE rides ADD COLUMN expired_at TIMESTAMPTZ;

-- Indice parcial para o scan do job de expiracao: filtra por created_at apenas
-- entre as corridas que ainda estao no mural (conjunto pequeno e quente).
CREATE INDEX idx_rides_mural_created_at ON rides (created_at) WHERE status = 'AVAILABLE_IN_MURAL';
