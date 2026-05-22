-- ============================================================================
-- V5: Taxa de cancelamento por corrida
-- ============================================================================
-- Coluna nula = sem taxa (cancelamento gratuito). Valor > 0 = taxa cobrada do
-- passageiro por cancelar apos a janela de graca em DRIVER_EN_ROUTE.
-- A regra de aplicacao vive em CancellationPolicy (codigo), nao no banco.
-- ============================================================================

ALTER TABLE rides ADD COLUMN cancellation_fee NUMERIC(10, 2);

ALTER TABLE rides ADD CONSTRAINT chk_rides_cancellation_fee_nonneg
    CHECK (cancellation_fee IS NULL OR cancellation_fee >= 0);
