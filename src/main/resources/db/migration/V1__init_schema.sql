-- ============================================================================
-- V1: Schema inicial do UniMove
-- ============================================================================
-- Cobre: usuarios + roles, perfil de motorista (online/approved), corridas com
-- @Version (lock otimista), tracking do motorista (lat/lng), e cache de rotas OSRM.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- USERS — base para todas as roles (PASSAGEIRO / MOTORISTA / ADMIN)
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    phone           VARCHAR(20),
    role            VARCHAR(20)  NOT NULL,
    cidade          VARCHAR(80)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_users_role CHECK (role IN ('PASSAGEIRO', 'MOTORISTA', 'ADMIN'))
);

CREATE INDEX idx_users_cidade ON users (cidade);

-- ----------------------------------------------------------------------------
-- DRIVERS — info adicional para usuarios com role = MOTORISTA
-- ----------------------------------------------------------------------------
CREATE TABLE drivers (
    user_id         UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    approved        BOOLEAN      NOT NULL DEFAULT false,
    online          BOOLEAN      NOT NULL DEFAULT false,
    last_seen_at    TIMESTAMPTZ,
    vehicle_type    VARCHAR(10)  NOT NULL,
    vehicle_plate   VARCHAR(10)  NOT NULL,

    CONSTRAINT chk_drivers_vehicle_type CHECK (vehicle_type IN ('CARRO', 'MOTO'))
);

CREATE INDEX idx_drivers_approved_online ON drivers (approved, online);

-- ----------------------------------------------------------------------------
-- RIDES — nucleo do sistema. @Version (lock otimista) na coluna `version`.
-- ----------------------------------------------------------------------------
CREATE TABLE rides (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version                     BIGINT       NOT NULL DEFAULT 0,

    passageiro_id               UUID         NOT NULL REFERENCES users(id),
    motorista_id                UUID         REFERENCES users(id),

    cidade                      VARCHAR(80)  NOT NULL,

    lat_origem                  NUMERIC(10, 7) NOT NULL,
    lng_origem                  NUMERIC(10, 7) NOT NULL,
    lat_destino                 NUMERIC(10, 7) NOT NULL,
    lng_destino                 NUMERIC(10, 7) NOT NULL,

    distancia_km                NUMERIC(10, 3) NOT NULL,
    tempo_min                   INTEGER        NOT NULL,
    preco                       NUMERIC(10, 2) NOT NULL,

    status                      VARCHAR(30)    NOT NULL,
    payment_method              VARCHAR(10),
    pix_payload                 TEXT,

    -- Localizacao do motorista durante DRIVER_EN_ROUTE -> IN_PROGRESS
    driver_current_lat          NUMERIC(10, 7),
    driver_current_lng          NUMERIC(10, 7),
    driver_location_updated_at  TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    accepted_at                 TIMESTAMPTZ,
    started_at                  TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,

    cancelled_at                TIMESTAMPTZ,
    cancelled_by                VARCHAR(20),
    cancel_reason               TEXT,

    CONSTRAINT chk_rides_status CHECK (status IN (
        'PENDING_PAYMENT',
        'AVAILABLE_IN_MURAL',
        'DRIVER_EN_ROUTE',
        'IN_PROGRESS',
        'COMPLETED',
        'CANCELLED'
    )),
    CONSTRAINT chk_rides_payment_method CHECK (
        payment_method IS NULL OR payment_method IN ('DINHEIRO', 'PIX')
    ),
    CONSTRAINT chk_rides_cancelled_by CHECK (
        cancelled_by IS NULL OR cancelled_by IN ('PASSAGEIRO', 'MOTORISTA')
    )
);

-- Indice critico do mural: filtra por status + cidade na query mais quente do sistema
CREATE INDEX idx_rides_status_cidade ON rides (status, cidade) WHERE status = 'AVAILABLE_IN_MURAL';
CREATE INDEX idx_rides_passageiro_id ON rides (passageiro_id);
CREATE INDEX idx_rides_motorista_id ON rides (motorista_id) WHERE motorista_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- ROUTE_CACHE — evita chamadas repetidas ao OSRM para rotas comuns
-- Hash gerado pelo MapsService a partir de (latO, lngO, latD, lngD) arredondados a 4 casas decimais.
-- ----------------------------------------------------------------------------
CREATE TABLE route_cache (
    id              BIGSERIAL PRIMARY KEY,
    route_hash      VARCHAR(128) NOT NULL UNIQUE,
    distancia_km    NUMERIC(10, 3) NOT NULL,
    tempo_min       INTEGER        NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
