-- ============================================================================
-- V25: Pagamento em dinheiro com registro operacional
-- ============================================================================
-- payment_method ja existia (V1). Agora a corrida diz se o dinheiro foi de
-- fato recebido: o motorista registra o valor ao concluir e divergencias
-- (pagou menos / nao pagou) ficam marcadas para conciliacao no admin.
-- Pix continua sem PSP e fica SIMULATED: nunca conta como recebido.
-- ============================================================================

ALTER TABLE rides
    ADD COLUMN payment_status       VARCHAR(20)              NULL,
    ADD COLUMN cash_change_for      NUMERIC(10, 2)           NULL,
    ADD COLUMN cash_received_amount NUMERIC(10, 2)           NULL,
    ADD COLUMN payment_confirmed_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN payment_note         VARCHAR(500)             NULL;

ALTER TABLE rides ADD CONSTRAINT chk_rides_payment_status
    CHECK (payment_status IN ('PENDING', 'CONFIRMED', 'DIVERGENT', 'SIMULATED'));

-- Corridas anteriores: Pix sempre foi simulado; dinheiro nunca foi registrado,
-- entao fica PENDING (honesto: ninguem confirmou o recebimento).
UPDATE rides SET payment_status = 'SIMULATED' WHERE payment_method = 'PIX';
UPDATE rides SET payment_status = 'PENDING'   WHERE payment_method = 'DINHEIRO';

-- Fila de conciliacao do admin: dinheiro pendente ou divergente.
CREATE INDEX idx_rides_payment_reconciliation ON rides (completed_at DESC)
    WHERE payment_status IN ('PENDING', 'DIVERGENT');
