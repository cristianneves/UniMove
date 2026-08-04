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

Os testes sao unitarios (JUnit 5 + Mockito) e nao dependem de Postgres nem de Docker — rodam em segundos. Validacao com banco real e feita manualmente via `docs/smoke-test.md` + `docs/api.http`.

---

## Estrutura do projeto

Monolito modular por dominio (detalhes em `CLAUDE.md`):

```
com.unimove
├── domain.user       Auth, JWT, driver online/offline, admin, favoritos,         [implementado]
│                     ratings, suspensao de usuario
├── domain.maps       Gateway OSRM (rotas + geometria/polyline) + cache de rotas,  [implementado]
│                     gateway Photon (geocoding: busca de endereço + pin no mapa)
├── domain.ride       Mural, maquina de estados, tarifa dinamica (pricing_configs), [implementado]
│                     polling, estimate, rating bi, taxa de cancelamento,
│                     categorias MOTO/CARRO, earnings do motorista,
│                     compartilhamento publico da viagem (/share/{token})
├── domain.chat       Chat in-app via SSE entre passageiro e motorista             [implementado]
├── domain.payment    Simulacao Pix + Dinheiro (BR Code ficticio)                  [implementado]
└── shared            Config, security, exception handler, utils                   [implementado]
```

---

## Documentacao da API

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`
- **Coleção HTTP versionada:** [`docs/api.http`](./docs/api.http) — abre direto no IntelliJ HTTP Client ou VSCode REST Client.
- **Smoke test ponta-a-ponta:** [`docs/smoke-test.md`](./docs/smoke-test.md) — checklist manual de ~10 min para validar release.
- **Estado atual:** [`docs/estado-atual-projeto.md`](./docs/estado-atual-projeto.md) — snapshot do que existe e do fluxo ponta-a-ponta.
- **Visão geral e fluxo no Swagger:** [`docs/visao-geral-e-fluxo-swagger.md`](./docs/visao-geral-e-fluxo-swagger.md) — porquê de cada decisão de MVP + roteiro completo.
- **Painel de métricas (admin):** [`docs/admin-metrics.md`](./docs/admin-metrics.md) — contrato do `GET /admin/metrics`.
- **Login social:** [`docs/login-social-google.md`](./docs/login-social-google.md) — fluxo de ID Token, vinculação de contas e configuração no Google Cloud.
- **Login social — guia do app:** [`docs/guia-app-login-google.md`](./docs/guia-app-login-google.md) — passo a passo para o dev mobile: OAuth clients de plataforma, `google_sign_in` e os dois fluxos de tela.
- **Surge pricing:** [`docs/plano-surge-pricing.md`](./docs/plano-surge-pricing.md) — spec do preço dinâmico por demanda.
- **Análise/roadmap:** [`docs/analise-mvp.md`](./docs/analise-mvp.md) — lacunas priorizadas para o piloto real.
- **Deploy:** [`docs/deploy-vps.md`](./docs/deploy-vps.md) — provisionamento da VPS, secrets do GitHub, rollback e backup.

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
| `DOMAIN`              | —                                  | **so em producao** — dominio publico que o Caddy usa para emitir o TLS |
| `IMAGE_TAG`           | `latest`                           | nao — escrito pelo `scripts/deploy.sh`, nao editar na mao |

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
[test]   mvn -B verify (196 testes, ~1min, sem Docker/Postgres)
[build]  buildx --platform linux/arm64 -> ghcr.io/cristianneves/unimove:<sha> + :latest
[deploy] scp da infra + ssh na VPS -> scripts/deploy.sh <sha>
         pull, up -d, poll /actuator/health; se nao ficar UP, volta a tag anterior
```

Pull request na `main` roda **so** o job de testes — funciona como gate de merge.

Alvo: VPS Oracle Cloud Always Free (Ampere A1, **ARM64**, Sao Paulo), com Postgres
no proprio host e Caddy emitindo TLS. Passo a passo do provisionamento, secrets e
rollback em [`docs/deploy-vps.md`](./docs/deploy-vps.md).

> O `Dockerfile` e **so de runtime**: espera um `app.jar` pronto no contexto (o CI
> compila antes). Para buildar a imagem na mao:
> ```bash
> ./mvnw package && cp target/unimove-backend-*.jar app.jar && docker build -t unimove .
> ```
> Ele nao tem nenhum `RUN` de proposito — e o que permite montar a imagem ARM64
> num runner x86 sem emulacao QEMU.

---

## Convencoes

- **Mensagens de commit:** estilo conventional commits (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`)
- **Branches:** `main` (estavel) + feature branches (`feat/nome-curto`)
- **Migrations:** nunca editar migration ja aplicada — sempre criar nova `V{n+1}__fix_xxx.sql`
- **PRs:** antes de abrir, ler diretrizes criticas em `CLAUDE.md` (lock otimista, short polling, escopo por cidade, cache OSRM, etc)

---

## Status do MVP

Backend em **estado MVP-funcional** — todos os endpoints da matriz da `CLAUDE.md` existem e respondem com as roles corretas. Veja `docs/estado-atual-projeto.md` para o diagnostico completo.

| Bloco                       | Status        | Observacoes |
|-----------------------------|---------------|-------------|
| Scaffold (pom, profiles)    | concluido     | Spring Boot 3.3.5 + Java 21 |
| Schema (`V1`-`V20`)         | concluido     | users (com `status`, `phone_verified_at`, telefone unico e `password_hash` nullable p/ conta social), social_identities, phone_verifications, drivers, rides (com `@Version`, `share_token`, `route_geometry` e `surge_multiplier`), route_cache (com `geometry`), ride_ratings, saved_places, cancellation_fee, category, pricing_configs (com `surge_enabled`/`surge_cap`), chat_messages, ride_stops, geocode_cache |
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
| Suspensao de usuario        | concluido     | `POST /admin/users/{id}/suspend|reactivate`; enforcement assimetrico (login + acoes de escrita) |
| Share publico da viagem     | concluido     | `share_token` em toda ride; `GET /share/{token}` publico, 410 ao terminar |
| Tarifa configuravel         | concluido     | `pricing_configs(cidade, category, base, per_km, per_min)` + cache em memoria; ADMIN edita via `PUT /admin/pricing` |
| Surge pricing (preco dinamico) | concluido  | `SurgePolicy` — multiplicador automatico por demanda/oferta (cidade+categoria), ladder em degraus com teto; opt-in por cidade (`surge_enabled`/`surge_cap`). Congelado em `rides.surge_multiplier` no create; exposto no `estimate` |
| Chat in-app via SSE         | concluido     | `chat_messages.seq BIGSERIAL` + `Last-Event-ID` pra reconexao; heartbeat 15s |
| Multiplas paradas           | concluido     | `stops` (max 5) em `POST /rides`/`/estimate`; tabela `ride_stops`; rota como sequencia de waypoints |
| Geometria da rota (mapa)    | concluido     | OSRM `overview=full&geometries=polyline`; `GET /rides/{id}/route`, polyline no estimate/share; `route_geometry` + `route_cache.geometry` |
| Busca de endereco (geocoding) | concluido   | `GET /maps/geocode` (autocomplete) + `GET /maps/reverse` (pin) via Photon; `reverse` cacheado em `geocode_cache` |
| OpenAPI / Swagger UI        | concluido     | `springdoc-openapi` em `/swagger-ui.html` |
| Coleção HTTP / smoke test   | concluido     | `docs/api.http` + `docs/smoke-test.md` |

### Testes

Cobertura atual (`mvn test`) — 21 classes, **196 testes**:

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
- `UserProfileServiceTest` — edição de perfil, troca de senha, re-emissão de JWT ao mudar cidade, conta só-social sem senha (409 explicativo) e reset pelo admin que a converte em dual
- `AdminMetricsServiceTest` (Mockito) — painel admin: derivação de `active`/taxas/ticket médio, defaulting do período (últimos 30 dias), range invertido → 400, agregado nulo → zeros

Total: **196 testes** passando em segundos (JUnit 5 + Mockito, sem Docker/Postgres).

> **Lock otimista:** não é exercitado em unit test (depende do `@Version` do Hibernate em runtime). A garantia vem do schema (`rides.version`) + tradução de `ObjectOptimisticLockingFailureException` para HTTP 409 no `GlobalExceptionHandler`. Valide manualmente via `docs/smoke-test.md` seção 5 (aceite por dois motoristas).
