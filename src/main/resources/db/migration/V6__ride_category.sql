-- ============================================================================
-- V6: Categoria da corrida (MOTO / CARRO)
-- ============================================================================
-- Cada corrida nasce com uma categoria que casa com o VehicleType do motorista.
-- Mural filtra por categoria → motorista de moto so ve corridas MOTO, etc.
-- Default na migration so atende rides historicas; novas rides sempre vem com
-- categoria explicita no service (default CARRO se passageiro nao escolher).
-- ============================================================================

ALTER TABLE rides ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'CARRO';

ALTER TABLE rides ADD CONSTRAINT chk_rides_category
    CHECK (category IN ('MOTO', 'CARRO'));

-- Tira o default depois do backfill — entidade deve setar explicitamente.
ALTER TABLE rides ALTER COLUMN category DROP DEFAULT;

-- Reescreve o indice critico do mural para incluir categoria no filtro.
DROP INDEX IF EXISTS idx_rides_status_cidade;
CREATE INDEX idx_rides_status_cidade_cat
    ON rides (status, cidade, category)
    WHERE status = 'AVAILABLE_IN_MURAL';
