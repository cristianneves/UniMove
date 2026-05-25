-- ============================================================================
-- V9: Configuracao dinamica de tarifa por cidade e categoria
-- ============================================================================
-- Tira a formula hardcoded de PricingPolicy e move para tabela editavel via
-- painel ADMIN. Resolucao: tenta (cidade, category); se nao houver, cai no
-- (_DEFAULT, category). Seed preserva os valores anteriores:
--   CARRO:  base 5.50  per_km 2.10  per_min 0.20
--   MOTO :  base 3.85  per_km 1.47  per_min 0.14   (= CARRO * 0.7)
-- ============================================================================

CREATE TABLE pricing_configs (
    id                    UUID PRIMARY KEY,
    cidade                VARCHAR(80)              NOT NULL,
    category              VARCHAR(20)              NOT NULL,
    base                  NUMERIC(10,2)            NOT NULL,
    per_km                NUMERIC(10,2)            NOT NULL,
    per_min               NUMERIC(10,2)            NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by_admin_id   UUID,

    CONSTRAINT uk_pricing_cidade_cat UNIQUE (cidade, category),
    CONSTRAINT chk_pricing_category  CHECK (category IN ('MOTO', 'CARRO')),
    CONSTRAINT chk_pricing_base      CHECK (base    >= 0),
    CONSTRAINT chk_pricing_per_km    CHECK (per_km  >= 0),
    CONSTRAINT chk_pricing_per_min   CHECK (per_min >= 0)
);

INSERT INTO pricing_configs (id, cidade, category, base, per_km, per_min) VALUES
    (gen_random_uuid(), '_DEFAULT', 'CARRO', 5.50, 2.10, 0.20),
    (gen_random_uuid(), '_DEFAULT', 'MOTO',  3.85, 1.47, 0.14);
