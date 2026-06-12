-- ============================================================================
-- V17: Configuracao de surge pricing (preco dinamico por demanda) por cidade
-- ============================================================================
-- Estende pricing_configs com o controle de surge. A ladder (faixas ratio ->
-- multiplicador) e regra de produto fixa no codigo (SurgePolicy); aqui ficam
-- apenas o liga/desliga e o teto, editaveis por cidade+categoria via ADMIN.
--   surge_enabled: surge so atua quando ligado (default desligado, opt-in).
--   surge_cap:     teto do multiplicador (default 1.50; faixa 1.00..3.00).
-- ============================================================================

ALTER TABLE pricing_configs
    ADD COLUMN surge_enabled BOOLEAN       NOT NULL DEFAULT false,
    ADD COLUMN surge_cap     NUMERIC(3,2)  NOT NULL DEFAULT 1.50;

ALTER TABLE pricing_configs
    ADD CONSTRAINT chk_pricing_surge_cap CHECK (surge_cap >= 1.00 AND surge_cap <= 3.00);
