-- ============================================================================
-- V8: Compartilhamento publico da viagem em tempo real
-- ============================================================================
-- Token gerado automaticamente na criacao da ride. Endpoint publico /share/{token}
-- expoe um payload reduzido (sem PII sensivel) para o passageiro compartilhar
-- com familia/contatos via link. Token vale ate a ride entrar em estado final.
-- ============================================================================

ALTER TABLE rides ADD COLUMN share_token UUID;

ALTER TABLE rides ADD CONSTRAINT uk_rides_share_token UNIQUE (share_token);

-- Backfill: rides antigas em estado final nao precisam de token (endpoint
-- responde 410 GONE para elas mesmo se alguem tentar adivinhar). Rides
-- em estado nao-final em producao no momento da migration devem receber
-- um token para nao quebrar o app.
UPDATE rides SET share_token = gen_random_uuid()
    WHERE status IN ('PENDING_PAYMENT','AVAILABLE_IN_MURAL','DRIVER_EN_ROUTE','IN_PROGRESS');
