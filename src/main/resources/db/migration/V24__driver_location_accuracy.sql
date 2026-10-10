-- ============================================================================
-- V24: Precisao reportada pelo GPS na ultima posicao do motorista
-- ============================================================================
-- driver_location_updated_at passa a guardar o instante em que o GPS fixou a
-- posicao (quando o app o envia), nao o de chegada no servidor: e o que
-- permite descartar posicao atrasada/fora de ordem. A precisao fica junto
-- para auditoria de disputa ("o motorista estava onde disse?").
-- ============================================================================

ALTER TABLE rides ADD COLUMN driver_location_accuracy_m NUMERIC(8, 1) NULL;
