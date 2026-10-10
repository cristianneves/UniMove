-- ============================================================================
-- V31: CPF e CNH cifrados (AES-256-GCM) + fingerprint HMAC para unicidade
-- ============================================================================
-- O valor cifrado tem IV aleatorio, entao nao serve para indice unico. A
-- unicidade passa para cpf_hash/cnh_number_hash (HMAC-SHA256 com chave da
-- aplicacao): igual para o mesmo documento, irreversivel sem a chave.
--
-- Valor em claro gravado antes desta versao (so ambientes de dev, a V27 ainda
-- nao foi para producao) continua legivel: o converter devolve sem prefixo
-- "v1:" como esta, e cifra na proxima gravacao.
-- ============================================================================

DROP INDEX ux_drivers_cpf;
DROP INDEX ux_drivers_cnh_number;

ALTER TABLE drivers
    ALTER COLUMN cpf        TYPE VARCHAR(255),
    ALTER COLUMN cnh_number TYPE VARCHAR(255),
    ADD COLUMN cpf_hash        VARCHAR(64) NULL,
    ADD COLUMN cnh_number_hash VARCHAR(64) NULL;

CREATE UNIQUE INDEX ux_drivers_cpf_hash ON drivers (cpf_hash) WHERE cpf_hash IS NOT NULL;
CREATE UNIQUE INDEX ux_drivers_cnh_number_hash ON drivers (cnh_number_hash) WHERE cnh_number_hash IS NOT NULL;
