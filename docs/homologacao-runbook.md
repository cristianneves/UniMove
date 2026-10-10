# Runbook de homologação — API, banco e migrations V23–V28

Card: **[RNF06][P0] Homologação da API, banco, V19 e observabilidade**. Este documento cobre a parte de backend/banco. A parte de infraestrutura (health no Render, alertas, disponibilidade) depende de quem opera o ambiente e está marcada como pendente no fim.

## 1. A "V19 potencialmente destrutiva"

`V19__phone_verification.sql` **apaga todas as corridas e todos os usuários não-ADMIN** (`DELETE FROM rides; DELETE FROM users WHERE role <> 'ADMIN'`). Foi decisão consciente na época: a base era de teste e passou a aceitar só contas com telefone verificado.

- Ela só roda **uma vez por banco**. Num banco que já está em V19 ou acima ela **não roda de novo**.
- O risco real é um banco **com dados reais e ainda abaixo de V19**, por exemplo um restore antigo. Antes de apontar a API para qualquer banco, confira a versão:

```sql
SELECT version, description, installed_on, success
FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

Se a versão for menor que 19 e houver usuários reais, **não suba a API**. Faça backup e decida antes.

## 2. Pré-checagem das migrations novas (V23–V28)

Todas são aditivas, exceto pelos dois pontos abaixo. Rode estas consultas no banco de produção **antes** do deploy:

```sql
-- V23 cria índice ÚNICO de corrida ativa. Se houver duplicata, a migration falha e o deploy para.
SELECT passageiro_id, count(*) FROM rides
WHERE status IN ('PENDING_PAYMENT','AVAILABLE_IN_MURAL','DRIVER_EN_ROUTE','IN_PROGRESS')
GROUP BY passageiro_id HAVING count(*) > 1;

SELECT motorista_id, count(*) FROM rides
WHERE status IN ('DRIVER_EN_ROUTE','IN_PROGRESS')
GROUP BY motorista_id HAVING count(*) > 1;
```

Se alguma consulta retornar linhas, resolva cada caso **manualmente**: cancele a corrida órfã com o próprio passageiro ou motorista ciente. A migration não faz isso sozinha de propósito.

**V27 (efeito colateral esperado):** motorista aprovado **antes** desta versão fica sem CNH cadastrada e **deixa de conseguir ficar online** até completar a documentação (`/drivers/me/documentation`) e ser reaprovado. Avise os motoristas do piloto antes do deploy.

**V25:** faz backfill. Corrida antiga em Pix vira `SIMULATED` e em dinheiro vira `PENDING`.

## 3. Backup e rollback

1. **Antes do deploy:** faça backup do banco no Supabase (Database → Backups) **e** um `pg_dump` local. Restaure o dump num banco descartável e rode `SELECT count(*) FROM users;` para provar que ele restaura.
2. **Rollback de código:** volte para a imagem anterior (ver `docs/deploy.md`). As migrations V23–V28 só **adicionam** colunas, tabelas e índices, então a versão anterior da API sobe normalmente com elas aplicadas. O `ddl-auto=validate` ignora colunas a mais.
3. **Rollback de banco:** só restaurando o backup. O Flyway não desfaz migration. Use apenas se uma migration corromper dado, o que não deve acontecer nestas.

## 4. Bucket de documentos (RF02)

1. Supabase → Storage → **New bucket** `driver-documents`, com **Public desligado**.
2. Não crie policy pública. O backend usa a chave `service_role`, que ignora RLS.
3. Configure no Render: `SUPABASE_URL=https://<ref>.supabase.co` e `SUPABASE_SERVICE_KEY=<service_role>`. **Nunca** coloque essa chave no app.
4. Sem essas variáveis o upload responde **503** e o resto da API funciona normalmente.

## 5. Variáveis novas

| Variável | Para quê |
|---|---|
| `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` | upload e leitura dos documentos |
| `PRIVACY_TERMS_VERSION`, `PRIVACY_POLICY_URL` | versão e link da política exigidos no cadastro |
| `RATE_LIMIT_*`, `DRIVER_LOCATION_*` | opcionais; os defaults servem para o piloto |

## 6. Segurança operacional

- No boot a API registra um `ERROR SEGURANCA` se algum admin ainda usa a senha do seed (`V2`). **Troque já:** faça login como admin e chame `PUT /users/me/password`. A senha antiga está escrita no arquivo da migration, que não pode ser editado (o Flyway valida o checksum).
- Conta suspensa perde o acesso **na hora**: o filtro JWT consulta o status a cada request.

## 7. Smoke autenticado (validado em 09/10/2026)

Executado contra Postgres 16 limpo, com a API subindo do zero:

- [x] Flyway aplicou V1–V28; Hibernate `validate` e todas as queries JPQL aceitas no boot
- [x] `GET /legal/terms`, `GET /cities`, admin abre cidade
- [x] Desafio WhatsApp (canal LOG) → cadastro passageiro e motorista com `termsVersion`; termos antigos → 400
- [x] Documentação: perfil inválido → 400, válido → salvo; upload sem bucket → 503; aprovar sem documentos → 409; com documentos → 200; online
- [x] Corrida: estimativa (OSRM real), criação, segunda corrida → 409, dinheiro com troco, mural com troco, aceite, GPS (válido, velho, impreciso, salto impossível, fora de ordem), start, complete (posição apagada)
- [x] Dinheiro divergente, registro duplo → 409, conciliação `/admin/rides?paymentStatus=DIVERGENT`, `/admin/metrics.payments`
- [x] Reprovar documento → motorista perde aprovação e vê o motivo; auditoria registra tudo
- [x] Suspensão → token 403 na hora; rota pública segue 200
- [x] Preferências, exportação, exclusão (senha errada → 400; ok → 204; token → 401; linha anonimizada; telefone liberado)
- [x] Rate limit de auth por IP (429 na 31ª) sem afetar outro IP
- [x] Nenhum 500 no log

O roteiro pode ser repetido com `docs/api.http` seguindo a mesma ordem.

## 8. Pendente (infra, fora do código)

- [ ] Health do Render com várias tentativas e logs. Um timeout isolado não prova indisponibilidade: o plano free hiberna.
- [ ] Monitoramento e alerta (ex.: UptimeRobot em `/actuator/health`) e disponibilidade esperada.
- [ ] Testar o WhatsApp real em homologação (`PHONE_VERIFICATION_CHANNEL=WHATSAPP`).
- [ ] Backup verificável agendado.
