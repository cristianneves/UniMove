-- ============================================================================
-- V11: Paradas intermediarias da corrida (multiplas paradas)
-- ============================================================================
-- A Ride mantem origem/destino nas colunas existentes. As paradas intermediarias
-- ordenadas ficam nesta tabela filha, mapeadas via @ElementCollection + @OrderColumn.
-- `seq` e 0-based (indice da lista gerenciado pelo Hibernate). O preco continua
-- sendo calculado pela rota total que passa por todos os waypoints (ver diretriz 17).
-- ----------------------------------------------------------------------------
CREATE TABLE ride_stops (
    ride_id     UUID           NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
    seq         INTEGER        NOT NULL,
    lat         NUMERIC(10, 7) NOT NULL,
    lng         NUMERIC(10, 7) NOT NULL,

    PRIMARY KEY (ride_id, seq)
);
