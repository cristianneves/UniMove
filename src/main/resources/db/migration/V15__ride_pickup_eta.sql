-- ============================================================================
-- V15: ETA do motorista ate a origem, calculado no aceite (UX "chega em ~X min")
-- ============================================================================
-- Quando o motorista aceita e o app envia sua posicao, o backend faz UMA
-- chamada ao OSRM (driver -> origem) e grava o tempo estimado de chegada.
-- Calculo pontual no aceite, NUNCA no polling de 5s (regra 10).
--
-- Ambas NULLABLE: aceite sem body (cliente antigo, ou GPS indisponivel) e
-- corridas anteriores a esta feature ficam sem ETA — o front cai em fallback.
-- ----------------------------------------------------------------------------
ALTER TABLE rides ADD COLUMN pickup_eta_min INT;
ALTER TABLE rides ADD COLUMN pickup_eta_at  TIMESTAMP WITH TIME ZONE;
