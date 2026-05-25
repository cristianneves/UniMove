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
├── domain.maps       Gateway OSRM + cache de rotas (MapsService)                  [implementado]
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
- **Estado atual:** [`docs/estado-atual-projeto.md`](./docs/estado-atual-projeto.md).

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
| `SPRING_PROFILES_ACTIVE` | `dev`                           | nao         |

Veja `.env.example` para o template completo.

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
| Schema (`V1`-`V10`)         | concluido     | users (com `status`), drivers, rides (com `@Version` e `share_token`), route_cache, ride_ratings, saved_places, cancellation_fee, category, pricing_configs, chat_messages |
| `shared` (security, JWT, exception handler) | concluido | `GlobalExceptionHandler` cobre validacao, lock otimista, `BusinessException` |
| `domain.user`               | concluido     | `/auth/*`, online/offline, admin approve, `/saved-places`, denormalizacao de rating, **suspensao/reativacao via `/admin/users/*`** |
| `domain.maps`               | concluido     | `MapsService` + `OsrmMapsService` (cache-aside via `route_cache`) |
| `domain.payment`            | concluido     | `SimulatedPaymentService` — BR Code ficticio (sem PSP real) |
| `domain.ride`               | concluido     | Criacao, estimate, mural por cidade+categoria, aceite (lock otimista), state machine, cancelamento com taxa, polling, rating bi, earnings, **share publico em `/share/{token}`** |
| `domain.chat`               | concluido     | **Chat in-app via SSE em `/chat/rides/{id}/*` — habilitado durante `DRIVER_EN_ROUTE`/`IN_PROGRESS`** |
| Estimativa de preço         | concluido     | `POST /rides/estimate` reusa OSRM + cache + `PricingPolicy` (cidade-aware) sem persistir |
| Rating bidirecional         | concluido     | `POST /rides/{id}/rating`, denormalizacao `rating_avg`/`rating_count` em `users` |
| Endereços favoritos         | concluido     | `POST/GET/DELETE /saved-places` (PASSAGEIRO) |
| Earnings do motorista       | concluido     | `GET /drivers/me/earnings?from=&to=` com breakdown por dia |
| Taxa de cancelamento        | concluido     | `CancellationPolicy` — R$ 3,00 após 120s de `DRIVER_EN_ROUTE` (passageiro) |
| Categorias MOTO/CARRO       | concluido     | Matching server-side no mural + accept, coeficientes por categoria |
| Suspensao de usuario        | concluido     | `POST /admin/users/{id}/suspend|reactivate`; enforcement assimetrico (login + acoes de escrita) |
| Share publico da viagem     | concluido     | `share_token` em toda ride; `GET /share/{token}` publico, 410 ao terminar |
| Tarifa configuravel         | concluido     | `pricing_configs(cidade, category, base, per_km, per_min)` + cache em memoria; ADMIN edita via `PUT /admin/pricing` |
| Chat in-app via SSE         | concluido     | `chat_messages.seq BIGSERIAL` + `Last-Event-ID` pra reconexao; heartbeat 15s |
| OpenAPI / Swagger UI        | concluido     | `springdoc-openapi` em `/swagger-ui.html` |
| Coleção HTTP / smoke test   | concluido     | `docs/api.http` + `docs/smoke-test.md` |

### Testes

Cobertura atual (`mvn test`):

- `AuthControllerWebMvcTest` (MockMvc) — fluxos de register/login
- `JwtServiceTest` — emissao e validacao de token
- `CityNormalizerTest` — normalizacao de cidade
- `RouteHasherTest` — hash deterministico das rotas OSRM
- `OsrmMapsServiceTest` — cache hit/miss, OSRM 5xx, payload vazio, race no insert
- `RideServiceTest` (Mockito, 29 cenários) — máquina de estados ponta-a-ponta, regras de role no cancelamento, gating do `driver-location`, delegação do mural por cidade + categoria, invariante de preço calculado no backend a partir do OSRM, submitRating cross-role

Total: **51 testes** passando em ~6 s (sem Docker/Postgres).

> **Lock otimista:** não é exercitado em unit test (depende do `@Version` do Hibernate em runtime). A garantia vem do schema (`rides.version`) + tradução de `ObjectOptimisticLockingFailureException` para HTTP 409 no `GlobalExceptionHandler`. Valide manualmente via `docs/smoke-test.md` seção 5 (aceite por dois motoristas).
