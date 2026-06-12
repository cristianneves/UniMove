-- ============================================================================
-- V18: Multiplicador de surge aplicado na corrida (auditoria + recibo)
-- ============================================================================
-- Grava o multiplicador de surge que estava vigente no momento da criacao da
-- corrida — congelado junto com o preco (rides.preco). 1.00 = sem surge.
-- Corridas anteriores recebem 1.00 (default). Serve a /admin/rides e ao recibo.
-- ============================================================================

ALTER TABLE rides
    ADD COLUMN surge_multiplier NUMERIC(3,2) NOT NULL DEFAULT 1.00;

ALTER TABLE rides
    ADD CONSTRAINT chk_rides_surge_multiplier CHECK (surge_multiplier >= 1.00);
