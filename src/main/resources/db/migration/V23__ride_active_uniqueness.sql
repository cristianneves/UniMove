-- ============================================================================
-- V23: Uma corrida ativa por passageiro e por motorista, garantida no banco
-- ============================================================================
-- O RideService ja checa com exists() antes de criar/aceitar, mas create()
-- roda sem transacao (a rota do OSRM fica fora dela) e o accept() idem: dois
-- toques rapidos no app passam juntos pelo exists() e geravam duas corridas.
-- O indice unico parcial fecha a janela; o service traduz a violacao para o
-- mesmo 409 do exists().
--
-- Se ja houver duplicata ativa a criacao do indice falha e o deploy para —
-- de proposito: corrigir na mao (docs/homologacao-runbook.md) e melhor que
-- cancelar corrida de alguem em silencio.
-- ============================================================================

CREATE UNIQUE INDEX ux_rides_passenger_active ON rides (passageiro_id)
    WHERE status IN ('PENDING_PAYMENT', 'AVAILABLE_IN_MURAL', 'DRIVER_EN_ROUTE', 'IN_PROGRESS');

CREATE UNIQUE INDEX ux_rides_driver_active ON rides (motorista_id)
    WHERE status IN ('DRIVER_EN_ROUTE', 'IN_PROGRESS');
