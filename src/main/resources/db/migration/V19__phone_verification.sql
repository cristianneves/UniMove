-- ============================================================================
-- V19: Verificacao de telefone via WhatsApp (fluxo reverso)
-- ============================================================================
-- O usuario abre um link wa.me com um codigo pre-preenchido e envia a mensagem
-- para o numero da UniMove. O webhook da Meta entrega a mensagem junto com o
-- wa_id do remetente — e esse wa_id, nao um campo de formulario, vira o
-- telefone da conta. Ninguem consegue cadastrar o numero de outra pessoa.
--
-- Custo zero e permanente: na Cloud API mensagens recebidas sao gratuitas e
-- so template ENVIADO pela empresa e cobrado. Como nunca enviamos nada, nao ha
-- cobranca nem quota que expire.
--
-- ATENCAO — esta migration APAGA dados de forma irreversivel (decisao tomada
-- em conjunto): todas as corridas e todos os usuarios nao-ADMIN. A base do
-- piloto era de teste e passa a ter apenas contas com telefone verificado.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Desafios de verificacao
-- ---------------------------------------------------------------------------
-- O `code` NAO e credencial: a prova de posse e o remetente que a Meta assina
-- e entrega; o codigo apenas liga a mensagem recebida ao desafio.
--
-- O `token` (devolvido ao app e consumido pelo /auth/register) e credencial
-- portadora, mas fica em claro de proposito: hashear so protegeria contra quem
-- ja tem dump do banco — e esse atacante ja domina todas as contas, entao o
-- ganho seria nulo. A defesa real e o TTL curto (minutos) e o uso unico.
-- Existe separado do `id` para nao trafegar em URL (o status vai por GET com
-- o id no path, e path costuma cair em log de proxy).
CREATE TABLE phone_verifications (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(16)              NOT NULL UNIQUE,
    status           VARCHAR(20)              NOT NULL,
    verified_phone   VARCHAR(20)              NULL,
    token            VARCHAR(64)              NULL,
    rejection_reason VARCHAR(40)              NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    token_expires_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT chk_phone_verifications_status
        CHECK (status IN ('PENDING', 'VERIFIED', 'CONSUMED', 'REJECTED', 'EXPIRED'))
);

-- Lookup do token no /auth/register. Parcial: so desafios verificados tem token.
CREATE UNIQUE INDEX ux_phone_verifications_token
    ON phone_verifications (token)
    WHERE token IS NOT NULL;

-- Usado pelo scheduler que expira PENDING vencidos e purga registros terminais.
CREATE INDEX idx_phone_verifications_expires_at ON phone_verifications (expires_at);

-- ---------------------------------------------------------------------------
-- 2. Marca de verificacao no usuario
-- ---------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN phone_verified_at TIMESTAMP WITH TIME ZONE NULL;

-- ---------------------------------------------------------------------------
-- 3. Limpeza da base de teste
-- ---------------------------------------------------------------------------
-- Ordem obrigatoria: rides.passageiro_id/motorista_id e ride_ratings.rater_id/
-- ratee_id referenciam users(id) SEM ON DELETE CASCADE (V1, V3), entao as
-- corridas precisam sair antes dos usuarios.
--   DELETE FROM rides  -> cascateia ride_stops, chat_messages, ride_ratings
--   DELETE FROM users  -> cascateia drivers, saved_places
-- O admin do seed (V2) e preservado; ele nao passa pelo cadastro publico e
-- por isso permanece com phone NULL e phone_verified_at NULL.
DELETE FROM rides;
DELETE FROM users WHERE role <> 'ADMIN';

-- ---------------------------------------------------------------------------
-- 4. Unicidade do telefone
-- ---------------------------------------------------------------------------
-- Depois da limpeza, senao telefones duplicados do periodo sem verificacao
-- quebrariam a criacao do indice. Parcial porque o admin tem phone NULL.
CREATE UNIQUE INDEX ux_users_phone ON users (phone) WHERE phone IS NOT NULL;
