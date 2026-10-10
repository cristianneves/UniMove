-- ============================================================================
-- V28: Privacidade (LGPD) — consentimento, preferencias e exclusao de conta
-- ============================================================================
-- * terms_version/terms_accepted_at: qual versao da politica de privacidade e
--   dos termos o usuario aceitou e quando (prova do consentimento).
-- * marketing_opt_in: comunicacao promocional so com opt-in explicito; a
--   transacional (status da corrida, seguranca) nao depende de preferencia.
-- * DELETED: exclusao a pedido do titular. A linha fica, anonimizada, porque
--   corridas e avaliacoes de terceiros apontam para ela (retencao legal); o
--   que identifica a pessoa e apagado na hora.
-- ============================================================================

ALTER TABLE users
    ADD COLUMN terms_version       VARCHAR(20)              NULL,
    ADD COLUMN terms_accepted_at   TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN marketing_opt_in    BOOLEAN                  NOT NULL DEFAULT FALSE,
    ADD COLUMN marketing_opt_in_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN deleted_at          TIMESTAMP WITH TIME ZONE NULL;

ALTER TABLE users DROP CONSTRAINT chk_users_status;
ALTER TABLE users ADD CONSTRAINT chk_users_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'));
