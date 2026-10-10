-- ============================================================================
-- V32: Notificacoes in-app e campanhas administrativas
-- ============================================================================
-- Sem push (fora do escopo do MVP): a campanha vira uma linha por destinatario
-- na caixa de entrada do app (GET /notifications). Campanha so alcanca quem deu
-- opt-in promocional (V28) e esta ativo.
-- ============================================================================

CREATE TABLE notification_campaigns (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title               VARCHAR(80)              NOT NULL,
    body                VARCHAR(500)             NOT NULL,
    target_cidade       VARCHAR(80)              NULL,
    target_role         VARCHAR(20)              NULL,
    status              VARCHAR(20)              NOT NULL,
    recipients          INTEGER                  NOT NULL DEFAULT 0,
    created_by_admin_id UUID                     NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sent_at             TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT chk_campaign_status CHECK (status IN ('SENT', 'NO_AUDIENCE')),
    CONSTRAINT chk_campaign_role CHECK (target_role IS NULL OR target_role IN ('PASSAGEIRO', 'MOTORISTA'))
);

CREATE INDEX idx_campaigns_created_at ON notification_campaigns (created_at DESC);

CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID                     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    campaign_id UUID                     NULL REFERENCES notification_campaigns(id) ON DELETE SET NULL,
    kind        VARCHAR(20)              NOT NULL,
    title       VARCHAR(80)              NOT NULL,
    body        VARCHAR(500)             NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    read_at     TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT chk_notification_kind CHECK (kind IN ('PROMOTIONAL', 'TRANSACTIONAL'))
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
