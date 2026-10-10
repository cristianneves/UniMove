-- ============================================================================
-- V30: Cancelamento administrativo de corrida
-- ============================================================================
-- O admin passa a poder cancelar corrida nao terminal (ex.: motorista sumiu
-- com a corrida em IN_PROGRESS). Fica registrado como ADMIN em cancelled_by.
-- ============================================================================

ALTER TABLE rides DROP CONSTRAINT chk_rides_cancelled_by;
ALTER TABLE rides ADD CONSTRAINT chk_rides_cancelled_by CHECK (
    cancelled_by IS NULL OR cancelled_by IN ('PASSAGEIRO', 'MOTORISTA', 'ADMIN')
);
