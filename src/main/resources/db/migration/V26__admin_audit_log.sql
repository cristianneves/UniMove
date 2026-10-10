-- ============================================================================
-- V26: Trilha de auditoria das acoes administrativas
-- ============================================================================
-- Append-only: quem fez o que, sobre quem, quando. Grava na mesma transacao
-- da acao — se a acao der rollback, o registro some junto; se o registro
-- falhar, a acao nao acontece. Inclui leitura de documento de motorista
-- (dado sensivel), nao so escrita.
-- ============================================================================

CREATE TABLE admin_audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id    UUID                     NOT NULL,
    action      VARCHAR(40)              NOT NULL,
    target_type VARCHAR(30)              NOT NULL,
    target_id   VARCHAR(100)             NOT NULL,
    details     VARCHAR(1000)            NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_audit_created_at ON admin_audit_log (created_at DESC);
CREATE INDEX idx_admin_audit_target ON admin_audit_log (target_type, target_id, created_at DESC);
