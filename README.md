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
- **JUnit 5 + Testcontainers** (testes com Postgres real)

---

## Pre-requisitos

- JDK 21 ([Temurin](https://adoptium.net/) recomendado)
- Maven 3.9+
- Docker Desktop (para Postgres local e Testcontainers)

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

Testcontainers sobe um Postgres real para cada execucao — Docker precisa estar rodando. Nao usamos H2 para evitar divergencia de tipos com producao.

---

## Estrutura do projeto

Monolito modular por dominio (detalhes em `CLAUDE.md`):

```
com.unimove
├── domain.user       Auth, registro, roles, JWT                       [implementado]
├── domain.maps       Gateway OSRM + cache de rotas (MapsService)      [implementado]
├── domain.ride       Mural, maquina de estados, tarifa                [pendente]
├── domain.payment    Simulacao Pix + Dinheiro                         [pendente]
└── shared            Config, security, exception handler, utils       [implementado]
```

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

Em desenvolvimento incremental. Implementacao por dominio, na ordem de dependencia (folhas primeiro):

| Bloco                       | Status        | Observacoes |
|-----------------------------|---------------|-------------|
| Scaffold (pom, profiles)    | concluido     | Spring Boot 3.3.5 + Java 21 |
| Schema inicial (`V1`)       | concluido     | users, drivers, rides (com `@Version`), route_cache |
| `shared` (security, JWT, exception handler) | concluido | `GlobalExceptionHandler` cobre validacao, lock otimista, `BusinessException` |
| `domain.user`               | concluido     | `/auth/register`, `/auth/login`, roles PASSAGEIRO/MOTORISTA/ADMIN |
| `domain.maps`               | concluido     | `MapsService` + `OsrmMapsService` (cache-aside via `route_cache`) |
| `domain.payment`            | proximo       | Payload Pix fictício; transicao PENDING_PAYMENT → AVAILABLE_IN_MURAL |
| `domain.ride`               | pendente      | Nucleo do MVP: criacao, mural, aceite (lock otimista), maquina de estados, polling |
| Endpoints driver/admin      | pendente      | `/drivers/me/online|offline`, `/admin/drivers/{id}/approve` |

### Testes

21 testes passando (`mvn test`). Cobertura atual:

- `AuthControllerWebMvcTest` (MockMvc) — fluxos de register/login
- `JwtServiceTest` — emissao e validacao de token
- `CityNormalizerTest` — normalizacao de cidade
- `RouteHasherTest` — hash deterministico das rotas OSRM
- `OsrmMapsServiceTest` — cache hit/miss, OSRM 5xx, payload vazio, race no insert

> **Nota:** atualmente nao ha teste de integracao com Postgres real (Testcontainers) habilitado nesta maquina por bug do Docker Desktop. Quando resolvido, recriar coberturas de integracao para `AuthService` e adicionar para `MapsService` (mapping JPA real vs schema).
