-- ============================================================================
-- V13: Geometria da rota (linha desenhada no mapa do app)
-- ============================================================================
-- O OSRM passa a ser chamado com overview=full&geometries=polyline; a polyline
-- codificada (precisao 5) do trajeto completo origem→...→destino e persistida
-- para o front desenhar a rota (render+tiles). Ver diretrizes 11 e 19.
--
-- Ambas as colunas sao NULLABLE: linhas de route_cache criadas antes desta
-- feature nao tem geometria (sao completadas sob demanda no proximo hit), e
-- corridas antigas tambem nao tem trajeto gravado.
-- ----------------------------------------------------------------------------
ALTER TABLE route_cache ADD COLUMN geometry TEXT;
ALTER TABLE rides       ADD COLUMN route_geometry TEXT;
