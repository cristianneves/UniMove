-- Login social (Google). O provedor substitui a SENHA, nunca o telefone: o
-- cadastro social continua passando pelo desafio do WhatsApp, entao phone e
-- phone_verified_at seguem preenchidos como no cadastro por senha.

-- Conta criada pelo Google nasce sem senha. Continua sendo possivel ganhar uma
-- depois via POST /admin/users/{id}/reset-password.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- Tabela separada (e nao colunas em users) por dois motivos: o subject e a
-- chave estavel do provedor — o e-mail da conta Google pode mudar — e um
-- segundo provedor (Apple) entra sem nova migration de coluna.
CREATE TABLE social_identities (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider   VARCHAR(20)  NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_social_identities_provider CHECK (provider IN ('GOOGLE')),
    -- uma identidade do provedor pertence a um unico usuario...
    CONSTRAINT ux_social_identities_subject UNIQUE (provider, subject),
    -- ...e um usuario tem no maximo uma conta por provedor.
    CONSTRAINT ux_social_identities_user UNIQUE (provider, user_id)
);

CREATE INDEX idx_social_identities_user ON social_identities (user_id);
