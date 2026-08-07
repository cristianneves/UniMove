-- ============================================================================
-- V22: Cidades atendidas (whitelist)
-- ============================================================================
-- Ate aqui users.cidade era texto livre: qualquer string virava um slug via
-- CityNormalizer e era aceita no cadastro. Como o mural filtra por igualdade
-- exata, um erro de digitacao ("remanso" vs "remanso-ba") criava dois mercados
-- incomunicaveis, e cadastro em cidade sem operacao deixava o motorista num
-- mural permanentemente vazio, sem nenhum sinal de erro.
--
-- Agora a cidade vem desta lista fechada, gerenciada pelo ADMIN em runtime
-- (POST/DELETE /admin/cities) e exposta ao app em GET /cities.
--
-- Desativar e soft (active = false): quem ja esta na cidade continua operando
-- normalmente — a validacao roda so nos caminhos de escrita —, apenas ninguem
-- novo a escolhe.
-- ============================================================================

CREATE TABLE served_cities (
    slug         VARCHAR(80)  PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT true,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Backfill: toda cidade ja em uso vira cidade atendida, para que nenhum
-- usuario existente fique impedido de editar o proprio perfil. Inclui
-- 'sao-jose-do-rio-preto' (V2__seed_admin.sql) — o admin desativa pelo painel
-- o que nao fizer parte da operacao.
INSERT INTO served_cities (slug, display_name)
SELECT DISTINCT cidade, cidade FROM users
ON CONFLICT (slug) DO NOTHING;
