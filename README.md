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
├── domain.user       Auth, registro, roles, JWT, driver online/offline, admin   [implementado]
├── domain.maps       Gateway OSRM + cache de rotas (MapsService)                [implementado]
├── domain.ride       Mural, maquina de estados, tarifa, polling                 [implementado]
├── domain.payment    Simulacao Pix + Dinheiro (BR Code ficticio)                [implementado]
└── shared            Config, security, exception handler, utils                 [implementado]
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
| Schema inicial (`V1`)       | concluido     | users, drivers, rides (com `@Version`), route_cache |
| `shared` (security, JWT, exception handler) | concluido | `GlobalExceptionHandler` cobre validacao, lock otimista, `BusinessException` |
| `domain.user`               | concluido     | `/auth/register`, `/auth/login`, online/offline, admin approve |
| `domain.maps`               | concluido     | `MapsService` + `OsrmMapsService` (cache-aside via `route_cache`) |
| `domain.payment`            | concluido     | `SimulatedPaymentService` — BR Code ficticio (sem PSP real) |
| `domain.ride`               | concluido     | Nucleo do MVP: criacao, mural, aceite (lock otimista), state machine, cancelamento por role, polling de localizacao |
| OpenAPI / Swagger UI        | concluido     | `springdoc-openapi` em `/swagger-ui.html` |
| Coleção HTTP / smoke test   | concluido     | `docs/api.http` + `docs/smoke-test.md` |

### Testes

Cobertura atual (`mvn test`):

- `AuthControllerWebMvcTest` (MockMvc) — fluxos de register/login
- `JwtServiceTest` — emissao e validacao de token
- `CityNormalizerTest` — normalizacao de cidade
- `RouteHasherTest` — hash deterministico das rotas OSRM
- `OsrmMapsServiceTest` — cache hit/miss, OSRM 5xx, payload vazio, race no insert
- `RideServiceTest` (Mockito) — máquina de estados ponta-a-ponta, regras de role no cancelamento, gating do `driver-location`, delegação do mural para o repository, invariante de preço calculado no backend a partir do OSRM

> **Lock otimista:** não é exercitado em unit test (depende do `@Version` do Hibernate em runtime). A garantia vem do schema (`rides.version`) + tradução de `ObjectOptimisticLockingFailureException` para HTTP 409 no `GlobalExceptionHandler`. Valide manualmente via `docs/smoke-test.md` seção 5 (aceite por dois motoristas).
