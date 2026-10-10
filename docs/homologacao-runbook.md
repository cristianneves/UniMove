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

**Trava automática (desde 10/10/2026):** a própria API recusa subir nesse cenário. O callback `DestructiveMigrationGuard` roda antes do Flyway e, se o banco estiver abaixo da V19 com usuário não-ADMIN, aborta o boot com `Banco abaixo da V19 com N usuario(s) nao-ADMIN`. Banco novo e banco já em V19+ passam direto. Para rodar a V19 de propósito, depois do backup testado, suba uma única vez com `UNIMOVE_ALLOW_DESTRUCTIVE_V19=true` e remova a variável em seguida. Validado contra Postgres 16: banco em V18 com 1 passageiro → boot abortado e usuário preservado; com a variável → migrou até a V35.

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

## 4.1 Chave de criptografia (V31)

CPF e número da CNH ficam cifrados (AES-256-GCM). Antes do deploy:

1. Gere a chave com `openssl rand -base64 32` e configure `DATA_ENCRYPTION_KEY` no Render. **Sem ela a API não sobe em prod.**
2. Guarde uma cópia da chave fora do Render (cofre de senhas). **Perdê-la torna CPF/CNH ilegíveis**, e trocá-la exige recifrar os dados.

## 5. Variáveis novas

| Variável | Para quê |
|---|---|
| `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` | upload e leitura dos documentos |
| `PRIVACY_TERMS_VERSION`, `PRIVACY_POLICY_URL` | versão e link da política exigidos no cadastro |
| `DATA_ENCRYPTION_KEY` | **obrigatória em prod**: cifra CPF/CNH |
| `RATE_LIMIT_*`, `DRIVER_LOCATION_*`, `RETENTION_*` | opcionais; os defaults servem para o piloto |
| `PAYMENT_PIX_ENABLED` | padrão `false`: piloto **só dinheiro**, `confirm-payment` com Pix responde 409. Só ligue com PSP real integrado (Pix simulado não cobra nada) |
| `UNIMOVE_ALLOW_DESTRUCTIVE_V19` | padrão `false`. Libera a V19 em banco com dados (ver §1). Nunca deixe ligada |

## 6. Segurança operacional

- O admin do seed que ainda usa a senha publicada na V2 **é obrigado a trocá-la no primeiro login** (V29): até trocar, só `PUT /users/me/password` funciona. Enquanto isso, o boot também registra um `ERROR SEGURANCA`. A senha antiga está escrita no arquivo da migration, que não pode ser editado (o Flyway valida o checksum).
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

## 7.1 Smoke do segundo ciclo (09/10/2026): 39/39

Postgres 16 limpo, V1–V32: senha temporária e revogação de sessão; CPF cifrado (`v1:`) com unicidade pelo hash; fila de documentos; auditoria da leitura de CPF/CNH; busca de usuários; preço travado (409); cancelamento em andamento (motorista e admin); filtros e detalhe de corridas; extrato por pagamento; histórico com endereço; troca de telefone; campanhas e caixa de entrada; **SSE encerrado na suspensão**; **retenção** (corrida de 6 anos anonimizada, recente intacta); exclusão limpando a caixa; rate limit com `X-Forwarded-For` forjado. Nenhum 500 no log.

## 7.2 Smoke automatizado e observabilidade

`scripts/smoke.sh <url>` é não destrutivo: health com retentativa (o plano free hiberna), readiness (com banco), versão e commit em `/actuator/info`, `/cities`, `/legal/terms`, desafio de telefone (expira sozinho) e 401 sem token. Com `SMOKE_EMAIL`/`SMOKE_PASSWORD` de uma conta de smoke, também login, `/users/me` e `/rides/history`. Sai com código 1 em qualquer falha e imprime data e HTTP de cada tentativa, que servem de evidência no card.

Endpoints públicos de observabilidade:

| Endpoint | Uso |
|---|---|
| `/actuator/health` | status geral (inclui banco), só `UP`/`DOWN`, sem detalhes |
| `/actuator/health/liveness` | o processo está vivo (não olha o banco, para queda do banco não reiniciar o container) |
| `/actuator/health/readiness` | pronto para tráfego (olha o banco). **Use este no monitor externo** |
| `/actuator/info` | versão e commit do build (o CI passa `-Dbuild.commit=<sha>`) |

Validado em 10/10/2026 contra Postgres 16 limpo: cold start → 2ª tentativa `200`, 11/11 PASS com conta de smoke; senha errada e host fora do ar → FAIL com código 1.

## 8. Pendente (infra, fora do código)

- [ ] Health do Render com várias tentativas e logs. Um timeout isolado não prova indisponibilidade: o plano free hiberna.
- [ ] Monitoramento e alerta (ex.: UptimeRobot em `/actuator/health/readiness`, intervalo de 5 min) e disponibilidade esperada.
- [ ] Criar a conta de smoke em homologação e rodar `scripts/smoke.sh` contra o Render, anexando a saída ao card.
- [ ] Testar o WhatsApp real em homologação (`PHONE_VERIFICATION_CHANNEL=WHATSAPP`).
- [ ] Backup verificável agendado.
