-- ============================================================================
-- V36: Proposito do desafio de telefone (recuperacao de senha sem sessao)
-- ============================================================================
-- O mesmo fluxo reverso do WhatsApp agora serve a "esqueci minha senha": o
-- usuario prova a posse do numero da conta e escolhe uma senha nova.
-- NEW_PHONE (cadastro/troca de numero) exige numero SEM conta; PASSWORD_RESET
-- exige numero COM conta. Desafios existentes eram todos de cadastro.
ALTER TABLE phone_verifications
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'NEW_PHONE',
    ADD CONSTRAINT chk_phone_verifications_purpose
        CHECK (purpose IN ('NEW_PHONE', 'PASSWORD_RESET'));
