-- ============================================================================
-- V27: Cadastro documental do motorista (RF02)
-- ============================================================================
-- 1. Dados declarados (CPF, CNH, veiculo completo) no proprio drivers.
-- 2. Arquivos (CNH, CRLV, selfie) em storage PRIVADO; aqui so metadados,
--    status de revisao e o caminho no bucket. Cada envio e uma linha nova:
--    reenvio de documento reprovado nao apaga o historico da analise.
--
-- Motorista ja aprovado antes desta versao fica sem CNH cadastrada e por isso
-- deixa de poder ficar online ate completar a documentacao — de proposito:
-- "so documentacao aprovada habilita operacao" (criterio do RF02).
-- ============================================================================

ALTER TABLE drivers
    ADD COLUMN cpf            VARCHAR(11) NULL,
    ADD COLUMN cnh_number     VARCHAR(11) NULL,
    ADD COLUMN cnh_category   VARCHAR(2)  NULL,
    ADD COLUMN cnh_expires_on DATE        NULL,
    ADD COLUMN vehicle_brand  VARCHAR(40) NULL,
    ADD COLUMN vehicle_model  VARCHAR(60) NULL,
    ADD COLUMN vehicle_year   SMALLINT    NULL,
    ADD COLUMN vehicle_color  VARCHAR(30) NULL;

ALTER TABLE drivers ADD CONSTRAINT chk_drivers_cnh_category
    CHECK (cnh_category IN ('A', 'B', 'C', 'D', 'E', 'AB', 'AC', 'AD', 'AE'));

-- Um CPF / uma CNH por conta de motorista. Parciais: quem ainda nao preencheu fica NULL.
CREATE UNIQUE INDEX ux_drivers_cpf ON drivers (cpf) WHERE cpf IS NOT NULL;
CREATE UNIQUE INDEX ux_drivers_cnh_number ON drivers (cnh_number) WHERE cnh_number IS NOT NULL;

CREATE TABLE driver_documents (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id            UUID                     NOT NULL REFERENCES drivers(user_id) ON DELETE CASCADE,
    type                 VARCHAR(10)              NOT NULL,
    status               VARCHAR(20)              NOT NULL,
    -- NULL depois que o arquivo e expurgado (exclusao de conta / retencao).
    storage_path         VARCHAR(255)             NULL,
    content_type         VARCHAR(50)              NOT NULL,
    size_bytes           INTEGER                  NOT NULL,
    rejection_reason     VARCHAR(500)             NULL,
    reviewed_by_admin_id UUID                     NULL,
    reviewed_at          TIMESTAMP WITH TIME ZONE NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_driver_documents_type   CHECK (type IN ('CNH', 'CRLV', 'SELFIE')),
    CONSTRAINT chk_driver_documents_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_driver_documents_driver ON driver_documents (driver_id, type, created_at DESC);
CREATE INDEX idx_driver_documents_pending ON driver_documents (created_at) WHERE status = 'SUBMITTED';
