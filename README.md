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
├── domain.user       Auth, registro, roles, JWT
├── domain.ride       Mural, maquina de estados, tarifa
├── domain.maps       Gateway OSRM (interface MapsService)
├── domain.payment    Simulacao Pix + Dinheiro
└── shared            Config, security, exception handler, utils
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

Em desenvolvimento inicial. Setup do repositorio concluido; proximo passo: `pom.xml` + estrutura de pacotes + `V1__init_schema.sql`.
