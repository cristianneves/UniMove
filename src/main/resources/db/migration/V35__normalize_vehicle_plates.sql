-- O cadastro gravava a placa como digitada (ex.: "ABC-1234") e o perfil
-- documental sem hifen ("ABC1234"): a primeira edicao parecia troca de
-- veiculo e invalidava o CRLV. A partir daqui toda placa e normalizada.
UPDATE drivers
SET vehicle_plate = UPPER(REPLACE(BTRIM(vehicle_plate), '-', ''))
WHERE vehicle_plate <> UPPER(REPLACE(BTRIM(vehicle_plate), '-', ''));
