-- ============================================================================
-- V7: Suspensao de usuario pelo ADMIN
-- ============================================================================
-- Usuario suspenso nao consegue logar nem executar acoes criticas
-- (criar corrida, aceitar corrida, ficar online). Polling (GET) continua
-- aceitando o token existente ate expirar — escolha consciente para nao
-- onerar o filtro JWT com SELECT por request.
-- ============================================================================

ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE users ADD CONSTRAINT chk_users_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED'));

ALTER TABLE users ADD COLUMN suspended_at        TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE users ADD COLUMN suspended_reason    VARCHAR(500)             NULL;
ALTER TABLE users ADD COLUMN suspended_by_admin_id UUID                   NULL;

-- Indice parcial para listagem de suspensos no painel admin.
CREATE INDEX idx_users_suspended ON users (suspended_at DESC)
    WHERE status = 'SUSPENDED';
