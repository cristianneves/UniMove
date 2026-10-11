# UniMove — Backend

App de mobilidade urbana (caronas + mototaxis) para cidades de pequeno porte. Backend monolitico modular em Spring Boot.

> **Diretrizes de arquitetura, regras de negocio e regras nao-negociaveis estao em [`CLAUDE.md`](./CLAUDE.md). Leia antes de codar.**

---

## Stack

- **Java 21** (LTS)
- **Spring Boot 3.3+**
- **PostgreSQL 16**
- **Maven** (build)
- **Flyway** (migrations)
- **OSRM** (API de mapas)
- **JWT** (auth stateless)
- **JUnit 5 + Mockito** (testes unitários com mocks)

---

## Pre-requisitos

- JDK 21 ([Temurin](https://adoptium.net/) recomendado)
- Maven 3.9+
- Docker Desktop (para o Postgres local via `docker compose`)

---

## Setup local

```bash
# 1. Subir Postgres
docker compose up -d

# 2. Copiar variaveis de ambiente
cp .env.example .env
# edite .env e troque o JWT_SECRET por uma chave real:
#   openssl rand -base64 48

# 3. Rodar a aplicacao
mvn spring-boot:run
```

API sobe em `http://localhost:8080`. Migrations Flyway aplicam automaticamente no startup.

---

## Testes

```bash
mvn test
```

Os testes unitarios (JUnit 5 + Mockito) nao dependem de Postgres nem de Docker — rodam em segundos.

Os testes de integracao (`*IT.java`, Failsafe) sobem a API inteira contra um **Postgres real** e cobrem o que so o banco garante (lock otimista, indices parciais, queries). Sem `IT_DATABASE_URL` eles sao pulados:

```bash
docker run -d --rm --name unimove-it -p 55432:5432 -e POSTGRES_PASSWORD=it postgres:16
IT_DATABASE_URL=jdbc:postgresql://localhost:55432/postgres ./mvnw verify
docker stop unimove-it
```

(`IT_DATABASE_USER`/`IT_DATABASE_PASSWORD` tem default `postgres`/`it`. Testcontainers nao e usado porque nao roda no Docker Desktop do time.) No CI um Postgres 16 de servico roda os IT em todo PR.

Os IT de contrato (`AuthContractIT`, `RideJourneyIT`) sao a referencia do que o app consome; a matriz RF/RNF com a evidencia de cada requisito esta em [`docs/matriz-requisitos-mvp.md`](./docs/matriz-requisitos-mvp.md).

### Smoke de um ambiente

```bash
scripts/smoke.sh https://<api>                                       # health, cidades, termos, desafio, 401
SMOKE_EMAIL=... SMOKE_PASSWORD=... scripts/smoke.sh https://<api>    # + login, perfil, historico
```

Nao destrutivo; sai com 1 em qualquer falha. Ver `docs/homologacao-runbook.md` §7.2.

---

## Estrutura do projeto

Monolito modular por dominio (detalhes em `CLAUDE.md`):

```
com.unimove
├── domain.user       Auth, JWT, driver online/offline, admin, favoritos,         [implementado]
│                     ratings, suspensao de usuario, privacidade (LGPD)
│   └── document      Cadastro documental do motorista (CPF/CNH/veiculo +        [implementado]
│                     CNH/CRLV/selfie em bucket privado do Supabase Storage)
├── domain.audit      Trilha de auditoria das acoes administrativas              [implementado]
├── domain.notification  Caixa de entrada in-app + campanhas admin (sem push)    [implementado]
├── domain.maps       Gateway OSRM (rotas + geometria/polyline) + cache de rotas,  [implementado]
│                     gateway Photon (geocoding: busca de endereço + pin no mapa)
├── domain.ride       Mural, maquina de estados, tarifa dinamica (pricing_configs), [implementado]
│                     polling, estimate, rating bi, taxa de cancelamento,
│                     categorias MOTO/CARRO, earnings do motorista,
│                     compartilhamento publico da viagem (/share/{token})
├── domain.city       Cidades atendidas (whitelist) — porta CityCatalog,           [implementado]
│                     `GET /cities` publico + admin de abertura/fechamento
├── domain.chat       Chat in-app via SSE entre passageiro e motorista             [implementado]
├── domain.payment    Pix simulado (BR Code ficticio); dinheiro com registro      [implementado]
│                     operacional e conciliacao vive em domain.ride
└── shared            Config, security, exception handler, utils                   [implementado]
```

---

## Documentacao da API

- **Swagger UI:** `http://localhost:8080/swagger-ui.html` — **restrito a ADMIN**: abre a tela de login em `/docs/login` (e-mail/senha de admin, com o mesmo lockout do app; admin com senha temporaria troca no app antes). Para o "Try it out", cole um JWT no botao **Authorize**.
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs` (mesma sessao de admin)
- **Coleção HTTP versionada:** [`docs/api.http`](./docs/api.http) — abre direto no IntelliJ HTTP Client ou VSCode REST Client.
- **Smoke test de um ambiente:** `scripts/smoke.sh <url>` (ver *Smoke de um ambiente* acima).
- **Visão geral e fluxo no Swagger:** [`docs/visao-geral-e-fluxo-swagger.md`](./docs/visao-geral-e-fluxo-swagger.md) — porquê de cada decisão de MVP + roteiro completo.
- **Login social:** [`docs/login-social-google.md`](./docs/login-social-google.md) — fluxo de ID Token, vinculação de contas e configuração no Google Cloud.
- **Login social — guia do app:** [`docs/guia-app-login-google.md`](./docs/guia-app-login-google.md) — passo a passo para o dev mobile: OAuth clients de plataforma, `google_sign_in` e os dois fluxos de tela.
- **Go/No-Go do piloto:** [`docs/go-no-go-piloto.md`](./docs/go-no-go-piloto.md) — decisões de escopo, critérios bloqueantes, riscos aceitos e rollback.
- **Matriz de requisitos:** [`docs/matriz-requisitos-mvp.md`](./docs/matriz-requisitos-mvp.md) — evidência do backend para RF01–RF10 e RNF01–RNF09.
- **Deploy:** [`docs/deploy.md`](./docs/deploy.md) — Render + Supabase, secrets do GitHub, rollback e limites do plano gratuito.
- **Homologação / runbook:** [`docs/homologacao-runbook.md`](./docs/homologacao-runbook.md) — checagens antes de aplicar V23–V28, bucket de documentos, smoke autenticado, backup e rollback.
- **LGPD — inventário de dados:** [`docs/lgpd-inventario-dados.md`](./docs/lgpd-inventario-dados.md) — o que coletamos, por quê, com quem compartilhamos, retenção e como atender o titular.
- **Contrato das novidades para o app:** [`docs/contrato-mvp-backend-gaps.md`](./docs/contrato-mvp-backend-gaps.md) — endpoints e campos novos/alterados desta versão (cadastro com `termsVersion`, documentos, dinheiro, GPS, privacidade).

---

## Variaveis de ambiente

| Variavel              | Default                            | Obrigatorio |
|-----------------------|------------------------------------|-------------|
| `DATABASE_URL`        | `jdbc:postgresql://localhost:5432/unimove` | nao |
| `DATABASE_USER`       | `unimove`                          | nao         |
| `DATABASE_PASSWORD`   | —                                  | **sim**     |
| `JWT_SECRET`          | —                                  | **sim**     |
| `JWT_EXPIRATION_MS`   | `86400000` (24h)                   | nao         |
| `OSRM_BASE_URL`       | `https://router.project-osrm.org`  | nao         |
| `PHOTON_BASE_URL`     | `https://photon.komoot.io`         | nao         |
| `SPRING_PROFILES_ACTIVE` | `dev`                           | nao         |
| `PHONE_VERIFICATION_CHANNEL` | `LOG`                       | nao — use `WHATSAPP` em producao |
| `WHATSAPP_BUSINESS_NUMBER` | —                             | so com `channel=WHATSAPP` |
| `WHATSAPP_APP_SECRET` | —                                  | so com `channel=WHATSAPP` |
| `WHATSAPP_WEBHOOK_VERIFY_TOKEN` | —                        | so com `channel=WHATSAPP` |
| `GOOGLE_LOGIN_ENABLED` | `false`                           | nao — `true` liga `/auth/social` |
| `GOOGLE_CLIENT_IDS`   | —                                  | so com `GOOGLE_LOGIN_ENABLED=true` |
| `GOOGLE_JWK_SET_URI`  | JWKS do Google                     | nao         |
| `SUPABASE_URL`        | —                                  | nao — sem ele o upload de documentos do motorista responde 503 |
| `SUPABASE_SERVICE_KEY` | —                                 | junto com `SUPABASE_URL` (chave **service_role**, so no backend) |
| `SUPABASE_DOCUMENTS_BUCKET` | `driver-documents`           | nao — bucket **privado** |
| `DATA_ENCRYPTION_KEY` | —                                  | **sim em prod** — 32 bytes base64 (`openssl rand -base64 32`); cifra CPF/CNH. Sem ela o boot de prod falha; perder a chave torna os dados ilegiveis |
| `RETENTION_ENABLED` / `RETENTION_RIDE_YEARS` / `RETENTION_REJECTED_DOCUMENT_DAYS` | `true` / `5` / `90` | nao — expurgo LGPD diario (03:30 BRT) |
| `PRIVACY_TERMS_VERSION` | `2026-10`                        | nao — versao vigente dos termos; trocar exige novo aceite |
| `PRIVACY_POLICY_URL`  | `https://unimove.app/privacidade`  | nao — link exibido no cadastro |
| `RATE_LIMIT_AUTH_MAX` / `RATE_LIMIT_AUTH_WINDOW_MINUTES` | `30` / `10` | nao — login/cadastro/social por IP |
| `RATE_LIMIT_MAPS_MAX` / `RATE_LIMIT_MAPS_WINDOW_MINUTES` | `60` / `1`  | nao — `/maps/*` por usuario |
| `DRIVER_LOCATION_MAX_AGE_SECONDS` / `_MAX_ACCURACY_M` / `_MAX_SPEED_KMH` | `120` / `100` / `180` | nao — sanidade do GPS |
| `PAYMENT_PIX_ENABLED` | `false`                            | nao — piloto **so dinheiro**: Pix (simulado, sem PSP) responde 409. Ligar so com PSP real |
| `UNIMOVE_ALLOW_DESTRUCTIVE_V19` | `false`                  | nao — libera a V19 (apaga usuarios/corridas) num banco abaixo dela com dados; sem isso o boot aborta |
| `PORT`                | —                                  | nao — injetado pelo Render; tem precedencia sobre `SERVER_PORT` |
| `DOMAIN`              | —                                  | so no `docker-compose.prod.yml` (self-host) — dominio que o Caddy usa no TLS |
| `IMAGE_TAG`           | `latest`                           | so no `docker-compose.prod.yml` (self-host) |

Nenhuma das variaveis de WhatsApp e necessaria para desenvolver: o default `channel=LOG` roda o fluxo
completo de verificacao localmente, sem conta na Meta e sem tunel (ver secao 0 de `docs/api.http`).
Elas so entram quando se liga o canal real, e ai `channel=WHATSAPP` com credencial faltando derruba o
startup de proposito. Ver `docs/verificacao-telefone.md`.

O mesmo vale para o login social: com `GOOGLE_LOGIN_ENABLED=false` (default) nenhum bean nasce e
`/auth/social` responde 503 — nao e preciso credencial do Google para desenvolver. `GOOGLE_CLIENT_IDS`
e uma **lista** separada por virgula porque o claim `aud` do `id_token` muda por plataforma (Android com
`serverClientId` devolve o client ID web; iOS devolve o client ID iOS). Ver `docs/login-social-google.md`.

Veja `.env.example` para o template completo.

---

## Deploy

Push na `main` dispara o pipeline em [`.github/workflows/deploy.yml`](./.github/workflows/deploy.yml):

```
[test]   mvn -B verify (473 unitarios + 23 IT contra Postgres 16 de servico)
[build]  buildx --platform linux/amd64 -> ghcr.io/cristianneves/unimove:<sha> + :latest
[deploy] deploy hook do Render com imgURL=<sha>, depois espera /actuator/health
```

Observabilidade publica: `/actuator/health` (so status), `/actuator/health/liveness`,
`/actuator/health/readiness` (inclui o banco; use no monitor externo) e `/actuator/info`
(versao + commit do build). O resto do actuator nao e exposto.

Pull request na `main` roda **so** o job de testes — funciona como gate de merge.

Alvo: **Render** (web service, plano free) + **Supabase** (Postgres). Passo a
passo, secrets e rollback em [`docs/deploy.md`](./docs/deploy.md).

> ⚠️ O plano free do Render **hiberna apos 15 min sem trafego**: o primeiro
> acesso depois disso leva 30–60 s e os `@Scheduled` (expiracao de corrida,
> auto-offline de motorista) nao rodam enquanto o servico dorme. Serve para
> demo e integracao do app; nao para o piloto real. Ver os limites em
> [`docs/deploy.md`](./docs/deploy.md).

O `Dockerfile` e **so de runtime**: espera um `app.jar` pronto no contexto (o CI
compila antes). Para buildar a imagem na mao:
```bash
./mvnw package && cp target/unimove-backend-*.jar app.jar && docker build -t unimove .
```

Para rodar a stack completa (API + Postgres + TLS) numa VPS propria, o
[`docker-compose.prod.yml`](./docker-compose.prod.yml) ja esta pronto.

---

## Convencoes

- **Mensagens de commit:** estilo conventional commits (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`)
- **Branches:** `main` (estavel) + feature branches (`feat/nome-curto`)
- **Migrations:** nunca editar migration ja aplicada — sempre criar nova `V{n+1}__fix_xxx.sql`
- **PRs:** antes de abrir, ler diretrizes criticas em `CLAUDE.md` (lock otimista, short polling, escopo por cidade, cache OSRM, etc)

---

## Status do MVP

Backend em **estado MVP-funcional** — todos os endpoints da matriz da `CLAUDE.md` existem e respondem com as roles corretas. Pendencias para o piloto em `docs/go-no-go-piloto.md`.

| Bloco                       | Status        | Observacoes |
|-----------------------------|---------------|-------------|
| Scaffold (pom, profiles)    | concluido     | Spring Boot 3.3.5 + Java 21 |
| Schema (`V1`-`V36`)         | concluido     | V36 proposito do desafio de telefone (recuperacao de senha). V33 um unico envio de documento em analise por tipo (indice parcial), V34 pagamento/categoria preferidos, V35 placas normalizadas. V29 revogacao de sessao/senha temporaria, V30 cancelamento admin, V31 CPF/CNH cifrados, V32 notificacoes. V23–V28: corrida ativa unica no banco, precisao do GPS, pagamento em dinheiro, auditoria admin, documentos do motorista, consentimento/exclusao (LGPD). |
| Schema ate `V22`            | concluido     | served_cities, users (com `status`, `phone_verified_at`, telefone unico e `password_hash` nullable p/ conta social), social_identities, phone_verifications, drivers, rides (com `@Version`, `share_token`, `route_geometry` e `surge_multiplier`), route_cache (com `geometry`), ride_ratings, saved_places, cancellation_fee, category, pricing_configs (com `surge_enabled`/`surge_cap`), chat_messages, ride_stops, geocode_cache |
| `shared` (security, JWT, exception handler) | concluido | `GlobalExceptionHandler` cobre validacao, JSON ilegivel/enum invalido (400), lock otimista, `BusinessException` |
| `domain.user`               | concluido     | `/auth/*`, online/offline, admin approve, `/saved-places`, denormalizacao de rating, **suspensao/reativacao via `/admin/users/*`** |
| Auto-cadastro sem escalacao | concluido     | `POST /auth/register` aceita apenas `PASSAGEIRO` ou `MOTORISTA` — `ADMIN` no body devolve 400 (`@AssertTrue` no `RegisterRequest`) e a guarda em `AuthService.register` devolve 403. Admin so existe via seed/migration (`V2__seed_admin.sql`) |
| Login social (Google)       | concluido     | `POST /auth/social` + `POST /auth/social/register`. Fluxo de **ID Token**: o app faz o sign-in nativo e o backend so valida a assinatura contra o JWKS publico — sem `oauth2-client`, sem redirect, sem `client_secret`. Conta existente com o mesmo e-mail e vinculada automaticamente, **so** quando o provedor confirma `email_verified`. A verificacao de telefone continua obrigatoria: o Google substitui a senha, nunca a posse do numero. Desligado por default (`503`); ver `docs/login-social-google.md` |
| Verificacao de telefone     | concluido     | `domain.verification` — cadastro exige posse do numero, provada por fluxo reverso na WhatsApp Cloud API (`/auth/phone/*` + `/webhooks/whatsapp`). Custo R$0 permanente: nunca enviamos mensagem, e o telefone da conta vem do `wa_id` que a Meta entrega, nao do formulario. **Validado ponta a ponta com WhatsApp real em 03/08/2026.** Para desenvolver nao e preciso configurar nada (`channel=LOG` e o default) — ver `docs/verificacao-telefone.md` |
| `domain.maps`               | concluido     | `MapsService` + `OsrmMapsService` (cache-aside via `route_cache`, polyline pro mapa); `GeocodingService` + `PhotonGeocodingService` (busca de endereço/`reverse` via Photon, `geocode_cache`) |
| `domain.payment`            | concluido     | `SimulatedPaymentService` — BR Code ficticio (sem PSP real) |
| `domain.ride`               | concluido     | Criacao, estimate, mural por cidade+categoria, aceite (lock otimista), state machine, cancelamento com taxa, polling, rating bi, earnings, **share publico em `/share/{token}`** |
| `domain.chat`               | concluido     | **Chat in-app via SSE em `/chat/rides/{id}/*` — habilitado durante `DRIVER_EN_ROUTE`/`IN_PROGRESS`** |
| Estimativa de preço         | concluido     | `POST /rides/estimate` reusa OSRM + cache + `PricingPolicy` (cidade-aware) sem persistir |
| Rating bidirecional         | concluido     | `POST /rides/{id}/rating`, denormalizacao `rating_avg`/`rating_count` em `users` |
| Endereços favoritos         | concluido     | `POST/GET/DELETE /saved-places` (PASSAGEIRO) |
| Earnings do motorista       | concluido     | `GET /drivers/me/earnings?from=&to=` com breakdown por dia |
| Painel de metricas (admin)  | concluido     | `GET /admin/metrics?from=&to=` — corridas, receita e usuarios + serie diaria; periodo por `created_at`, default 30 dias |
| Taxa de cancelamento        | concluido     | `CancellationPolicy` — R$ 3,00 após 120s de `DRIVER_EN_ROUTE` (passageiro) |
| Categorias MOTO/CARRO       | concluido     | Matching server-side no mural + accept, coeficientes por categoria |
| Suspensao de usuario        | concluido     | `POST /admin/users/{id}/suspend|reactivate`; **o filtro JWT consulta o status a cada request**: token de conta suspensa responde 403 na hora (excluida, 401); rota publica segue funcionando |
| Rate limit                  | concluido     | Login/cadastro/social/reset de senha por IP e `/maps/*` por usuario (429 + `Retry-After`), alem do lockout por e-mail e do teto do desafio do WhatsApp |
| Corrida ativa unica         | concluido     | Indices unicos parciais (V23) fecham a janela do clique duplo — passageiro e motorista; violacao vira o mesmo 409 |
| Sanidade do GPS             | concluido     | `PUT /rides/{id}/driver-location` aceita `recordedAt`/`accuracyM`; 422 para posicao velha, do futuro, imprecisa ou salto impossivel; fora de ordem e ignorada; posicao apagada ao encerrar a corrida |
| Pagamento em dinheiro       | concluido     | `changeFor` (troco) no `confirm-payment`; `POST /rides/{id}/cash-payment` (motorista registra o recebido; abaixo do preco = `DIVERGENT` com observacao); `paymentStatus` em corrida/historico/mural/admin; filtro `/admin/rides?paymentStatus=`; bloco `payments` em `/admin/metrics`. Pix segue `SIMULATED` — nunca conta como recebido |
| Cadastro documental (RF02)  | concluido     | `/drivers/me/documentation/*` (CPF/CNH/veiculo + upload CNH/CRLV/selfie) e `/admin/drivers/{id}/documentation|documents/*` (URL assinada auditada, aprovar, reprovar com motivo). `approve` exige documentacao aprovada; online/aceite exige CNH valida. Ver `docs/homologacao-runbook.md` para o bucket |
| Auditoria admin             | concluido     | `admin_audit_log` (V26) gravado na mesma transacao da acao; `GET /admin/audit?action=&targetId=` |
| Sessoes e senha             | concluido     | Troca/reset de senha revoga tokens antigos (401) e derruba streams SSE; senha temporaria obriga troca (`mustChangePassword`); admin do seed forcado a trocar. **Esqueci minha senha sem sessao:** desafio do WhatsApp com `purpose=PASSWORD_RESET` (numero precisa ter conta) + `POST /auth/password/reset` → 204 |
| Admin operacional           | concluido     | Busca de usuarios, fila de documentos pendentes, `/admin/rides` com filtros e detalhe, cancelamento administrativo de corrida |
| Notificacoes / campanhas    | concluido     | `domain.notification`: campanhas so para quem deu opt-in, segmentadas por cidade/papel, com previa, historico e auditoria; caixa de entrada `GET /notifications` |
| Retencao LGPD               | concluido     | Job diario anonimiza corridas > 5 anos e apaga arquivos de documento obsoletos > 90 dias |
| Privacidade (LGPD)          | concluido     | `GET /legal/terms`, `termsVersion` obrigatorio no cadastro, `POST /users/me/terms`, `GET/PUT /users/me/preferences` (opt-in promocional), `GET /users/me/data-export`, `DELETE /users/me` (anonimizacao imediata). Ver `docs/lgpd-inventario-dados.md` |
| Share publico da viagem     | concluido     | `share_token` em toda ride; o passageiro dono obtem o link em `GET /rides/{id}/share-link` (so com a corrida viva); `GET /share/{token}` publico, 410 ao terminar |
| Preferencias do pedido      | concluido     | `GET/PUT /users/me/preferences` guarda `preferredPaymentMethod`/`preferredCategory` do passageiro (pre-selecao no app); motorista → 400; Pix com `PAYMENT_PIX_ENABLED=false` → 400 |
| Cidades atendidas (whitelist) | concluido   | `served_cities` + `CityCatalog`. `cidade` deixou de ser texto livre: cadastro (comum e social) e `PUT /users/me` so aceitam cidade da lista (400 `"Ainda nao atendemos esta cidade."`). `GET /cities` publico alimenta o seletor do app; ADMIN abre/fecha em runtime por `POST`/`DELETE /admin/cities`, sem deploy. Desativar e **soft**: quem ja opera na cidade continua, ela so some das escolhas novas. Motorista que troca de cidade e derrubado para offline |
| Tarifa configuravel         | concluido     | `pricing_configs(cidade, category, base, per_km, per_min)` + cache em memoria; ADMIN edita via `PUT /admin/pricing`. **Independente da whitelist**: da pra configurar a tarifa antes de abrir a cidade |
| Surge pricing (preco dinamico) | concluido  | `SurgePolicy` — multiplicador automatico por demanda/oferta (cidade+categoria), ladder em degraus com teto; opt-in por cidade (`surge_enabled`/`surge_cap`). Congelado em `rides.surge_multiplier` no create; exposto no `estimate` |
| Chat in-app via SSE         | concluido     | `chat_messages.seq BIGSERIAL` + `Last-Event-ID` pra reconexao; heartbeat 15s |
| Multiplas paradas           | concluido     | `stops` (max 5) em `POST /rides`/`/estimate`; tabela `ride_stops`; rota como sequencia de waypoints |
| Geometria da rota (mapa)    | concluido     | OSRM `overview=full&geometries=polyline`; `GET /rides/{id}/route`, polyline no estimate/share; `route_geometry` + `route_cache.geometry` |
| Busca de endereco (geocoding) | concluido   | `GET /maps/geocode` (autocomplete) + `GET /maps/reverse` (pin) via Photon; `reverse` cacheado em `geocode_cache` |
| OpenAPI / Swagger UI        | concluido     | `springdoc-openapi` em `/swagger-ui.html`, so para ADMIN via login em `/docs/login` (cadeia de seguranca propria com sessao + CSRF; a API segue stateless) |
| Coleção HTTP / smoke test   | concluido     | `docs/api.http` + `scripts/smoke.sh` |

> **Contrato do cliente ao trocar de cidade:** `PUT /users/me` devolve um `token` novo quando a cidade muda, e o app deve **substituir o JWT e reabrir os streams SSE** (`/rides/mural/stream`, `/rides/{id}/status-stream`, `/chat/rides/{id}/stream`). Todos os tokens anteriores sao revogados (401 `"Sessão encerrada por alteração na conta (senha ou cidade)..."`, inclusive em outros aparelhos) e os streams abertos com eles sao encerrados pelo backend.

### Testes

Cobertura atual (`mvn test`) — **485 testes unitarios** + **24 de integracao** (`mvn verify` com `IT_DATABASE_URL`). Cards MVP de 10/10/2026: `AuthContractIT` (cadastro real com desafio WhatsApp, token de uso unico, cidade por slug, 401/403, recuperacao de senha pelo WhatsApp), `RideJourneyIT` (jornada passageiro × 2 motoristas × admin com SSE e chat reais, piloto so dinheiro), `ObservabilityIT` (health/probes/info) e `DestructiveMigrationGuardTest` (trava da V19). Go/No-Go do piloto em [`docs/go-no-go-piloto.md`](./docs/go-no-go-piloto.md). Reauditoria de 10/10/2026: `RideIT` (aceite concorrente por dois motoristas → 200/409, clique duplo no pedido, `?sort=` invalido → 400, link de compartilhamento), `DriverDocumentIT` (envio duplicado concorrente → 201/409), `SessionAndDocsIT` (revogacao por troca de cidade, login do Swagger), alem de `RideShareServiceTest`, `DocsAuthenticationProviderTest`, `DocsSecurityWebMvcTest`, `VehiclePlateTest` e `GlobalExceptionHandlerTest`. Segundo ciclo: `NotificationServiceTest`, `FieldCryptoTest`, `FieldCryptoSpringWiringTest`, `CnhTest`, `UserStreamRegistryTest`, `ClientIpTest`, `RideRetentionSchedulerTest`, `DocumentRetentionSchedulerTest`, `AdminRideControllerWebMvcTest` e casos novos nos testes de servico. Primeiro ciclo:

- `SecurityWebMvcTest` — cadeia de seguranca real: 401 sem/token adulterado, 403 por role, conta suspensa (403) e excluida (401) com token ainda valido, rota publica com token bloqueado, acesso cruzado (BOLA)
- `RateLimitFilterTest`, `UserAccountServiceTest`, `DriverServiceTest`, `AdminAuditServiceTest`
- `DriverLocationPolicyTest` — GPS velho/futuro/impreciso/salto impossivel/fora de ordem
- `DriverDocumentServiceTest`, `SupabaseDocumentStorageTest`, `DocumentFileInspectorTest`, `DriverDocumentControllerWebMvcTest`, `CpfTest`
- `AccountPrivacyServiceTest`, `RidePrivacyServiceTest`, `UserPrivacyControllerWebMvcTest`
- `RideControllerContractTest` — contrato HTTP de todos os endpoints de corrida (status, validacao, shape do JSON)

Ja existentes:

- `CityCatalogServiceTest` — guarda de cidade atendida (inexistente e desativada dao o mesmo erro), `open` normalizando o slug e reativando linha existente em vez de duplicar, nome que nao vira slug → 400, soft delete e 404 em slug desconhecido
- `CityControllerWebMvcTest` (MockMvc) — `GET /cities`, `POST /admin/cities` 201 (nova) vs 200 (reativada), nome em branco → 400, `DELETE` → 204 e 404

- `AuthControllerWebMvcTest` (MockMvc) — fluxos de register/login, `role: ADMIN` → 400 sem chegar ao service, role desconhecida → 400, `/auth/social` nos dois `status` + 401/503, `/auth/social/register` sem `verificationToken` → 400
- `AuthServiceRegisterTest` (Mockito) — bloqueio de auto-cadastro como ADMIN antes de qualquer escrita, passageiro normalizado + token, motorista nasce `approved=false`, e-mail duplicado → 409
- `AuthServiceLoginLockoutTest` — lockout após tentativas de login falhas; conta só-social recebe o mesmo 401 genérico (sem enumeração de usuário)
- `GoogleIdTokenVerifierTest` — audiência de outro app Google → 401, `email_verified` como boolean e como string, token sem `sub`/`email`, falha do decoder → 401, ausência de client ID derruba o bean
- `SocialAuthServiceTest` — identidade já vinculada, vinculação por e-mail verificado, `REGISTRATION_REQUIRED`, e-mail não verificado bloqueia antes de qualquer vínculo, usuário suspenso, cadastro social sem senha + motorista pendente, provedor não configurado → 503
- `LoginAttemptServiceTest` — janela/contagem do lockout
- `JwtServiceTest` — emissao e validacao de token
- `CityNormalizerTest` — normalizacao de cidade
- `RouteHasherTest` — hash deterministico das rotas OSRM
- `OsrmMapsServiceTest` — cache hit/miss (incl. backfill de geometria), OSRM 5xx, payload vazio, race no insert, polyline persistida
- `PhotonGeocodingServiceTest` — forward (sugestões + bias lat/lon), query vazia sem chamada, reverse cache miss/hit, Photon 5xx → 503, sem features → 503
- `RideServiceTest` (Mockito) — máquina de estados ponta-a-ponta, regras de role no cancelamento, gating do `driver-location`, delegação do mural por cidade + categoria, invariante de preço calculado no backend a partir do OSRM, submitRating cross-role, paradas, geometria da rota, pickup ETA e surge
- `SurgePolicyTest` — ladder por faixa, teto (`surge_cap`), oferta = 0 → teto, `surge_enabled = false` → 1.0x, cidade/categoria sem demanda → 1.0x
- `RideExpirationSchedulerTest` — expiração de corridas paradas no mural (EXPIRED)
- `DriverAutoOfflineSchedulerTest` — auto-offline de motorista por inatividade
- `UserProfileServiceTest` — edição de perfil, troca de senha, re-emissão de JWT ao mudar cidade, cidade fora da whitelist não altera nada, motorista derrubado para offline ao trocar de cidade (e mantido online quando só o nome muda), conta só-social sem senha (409 explicativo) e reset pelo admin que a converte em dual
- `AdminMetricsServiceTest` (Mockito) — painel admin: derivação de `active`/taxas/ticket médio, defaulting do período (últimos 30 dias), range invertido → 400, agregado nulo → zeros

Total: **485 testes unitarios** passando em segundos (JUnit 5 + Mockito, sem Docker/Postgres) e **24 de integracao** contra Postgres 16 real, que aplicam as migrations V1–V36 com `ddl-auto=validate`.

> **Lock otimista e indices parciais:** exercitados de verdade pelos IT — duas requisicoes simultaneas batem no `@Version` (aceite) e nos indices `ux_rides_passenger_active`/`ux_driver_documents_one_submitted`, e cada uma vira 409.
