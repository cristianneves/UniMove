-- Um unico envio em analise por motorista e tipo de documento.
--
-- O upload confere "pode enviar?" e grava depois, sem lock: um clique duplo
-- no app gerava dois SUBMITTED do mesmo tipo. A analise so aceita o envio
-- vigente (o mais novo), entao o mais antigo ficava preso na fila do admin.
--
-- 1) Saneia duplicatas existentes: mantem o mais novo, reprova os demais.
UPDATE driver_documents d
SET status           = 'REJECTED',
    rejection_reason = 'Envio duplicado substituído pelo mais recente.',
    reviewed_at      = NOW()
WHERE d.status = 'SUBMITTED'
  AND EXISTS (SELECT 1 FROM driver_documents n
              WHERE n.driver_id = d.driver_id
                AND n.type = d.type
                AND n.status = 'SUBMITTED'
                AND (n.created_at, n.id) > (d.created_at, d.id));

-- 2) Daqui em diante o banco fecha a corrida entre o check e o insert.
CREATE UNIQUE INDEX ux_driver_documents_one_submitted
    ON driver_documents (driver_id, type)
    WHERE status = 'SUBMITTED';
