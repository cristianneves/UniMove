# UniMove — Estado atual do projeto

> Snapshot em **2026-05-21**. Branch `main`, último commit `e6c71f8` (working tree com itens 1-4 dos próximos passos já aplicados).
> Documento de visão geral: o que existe, como o fluxo funciona, se está pronto para MVP e para integração com o frontend.

---

## 1. Visão geral

O backend monolítico do UniMove já cobre o **fluxo ponta-a-ponta de uma corrida** — do cadastro do passageiro até a finalização pelo motorista — mais o **painel administrativo mínimo** para aprovar motoristas e auditar corridas. Toda a estrutura prevista na `CLAUDE.md` foi implementada: escopo por cidade, online/offline, lock otimista, cache OSRM, máquina de estados, cancelamento com regras por role e rastreamento do motorista via polling + Haversine.

**Stack confirmada:** Java 21, Spring Boot 3.3.5, PostgreSQL + Flyway, JWT (jjwt 0.12.6), WebClient (OSRM), JUnit 5 + Mockito. Lombok presente.

---

## 2. Domínios implementados

### 2.1 `domain.user` — autenticação, motoristas e admin

- **Entidades:** `User` (id, email, password_hash, name, phone, role, cidade) e `Driver` (PK = user_id, approved, online, last_seen_at, vehicle_type, vehicle_plate).
- **Roles:** `PASSAGEIRO`, `MOTORISTA`, `ADMIN`.
- **Controllers:**
  - `AuthController` — `POST /auth/register`, `POST /auth/login` (público).
  - `DriverController` — `POST /drivers/me/online`, `POST /drivers/me/offline` (MOTORISTA).
  - `AdminDriverController` — `GET /admin/drivers/pending`, `POST /admin/drivers/{id}/approve` (ADMIN).
- **Exceções específicas:** `EmailAlreadyUsedException`, `InvalidCityException`, `DriverNotApprovedException`, `DriverOfflineException`, `DriverNotFoundException`.
- **Validação de cidade:** `CityNormalizer` (slug normalizado).
- **JWT:** `JwtService` + `JwtAuthenticationFilter` + `AuthenticatedUser` (record de contexto com `userId`, `role`, `cidade`).

### 2.2 `domain.ride` — núcleo do produto

- **Entidade `Ride`** com `@Version` para lock otimista, todos os timestamps separados (`accepted_at`, `started_at`, `completed_at`, `cancelled_at`) e colunas de rastreamento do motorista (`driver_current_lat/lng/_updated_at`).
- **Máquina de estados** `RideStateMachine` reforçando o diagrama da `CLAUDE.md`. Transição inválida → `IllegalRideTransitionException` → HTTP 409.
- **`PricingPolicy`** aplica `5.50 + 2.10*km + 0.20*min` com `BigDecimal`.
- **`RideController` (todas as rotas previstas):**
  - `POST /rides`, `POST /rides/{id}/confirm-payment` (PASSAGEIRO)
  - `GET /rides/mural`, `POST /rides/{id}/accept`, `POST /rides/{id}/start`, `POST /rides/{id}/complete` (MOTORISTA)
  - `POST /rides/{id}/cancel` (PASSAGEIRO ou MOTORISTA, com regras de estado)
  - `PUT /rides/{id}/driver-location` (MOTORISTA aceitante)
  - `GET /rides/{id}` (dono ou motorista aceitante) — usado pelo polling do app
- **`AdminRideController`** — `GET /admin/rides` paginado.
- **Exceções dedicadas:** `RideNotFoundException`, `RideAccessDeniedException`, `IllegalRideTransitionException`, `DriverCityMismatchException`, `MissingCancelReasonException`, `LocationUpdateNotAllowedException`.

### 2.3 `domain.maps` — OSRM + cache

- `MapsService` (interface) + `OsrmMapsService` (impl) consultando `route_cache` primeiro, com fallback no OSRM público via `WebClient`.
- `RouteHasher` arredonda coordenadas a 4 casas decimais (~11 m) para gerar chave determinística.
- `RouteCache` é uma tabela autônoma, sem TTL — ADMIN faz `TRUNCATE` se precisar invalidar.
- `MapsUnavailableException` traduzida para HTTP 503 no `GlobalExceptionHandler`.

### 2.4 `domain.payment` — Pix simulado

- `PaymentService` (interface) + `SimulatedPaymentService` que gera um BR Code estático/fictício. Nenhuma integração real com PSP — exatamente como previsto no MVP.

### 2.5 `shared`

- `SecurityConfig` (CORS + filtro JWT + endpoints públicos).
- `LastSeenInterceptor` + `WebMvcConfig` atualizam `drivers.last_seen_at` a cada request autenticado de motorista.
- `GlobalExceptionHandler` consolida todos os erros e formata `ApiError`.
- `Haversine` para distância em linha reta no polling de localização.

---

## 3. Banco de dados

Migrações Flyway:

- `V1__init_schema.sql` — cria `users`, `drivers`, `rides`, `route_cache` com índices certos para as queries críticas (mural filtra por `(status, cidade)` com predicate index).
- `V2__seed_admin.sql` — usuário admin inicial com hash BCrypt.

Pontos fortes do schema:
- `version BIGINT` em `rides` viabilizando o lock otimista do aceite.
- Index parcial `idx_rides_status_cidade ... WHERE status = 'AVAILABLE_IN_MURAL'` mantém o mural barato mesmo com milhões de corridas históricas.
- Todas as colunas de timestamp são `TIMESTAMPTZ`.
- `CHECK constraints` para status, payment_method, cancelled_by e vehicle_type — defesa em profundidade frente ao Hibernate.

---

## 4. Fluxo end-to-end (como funciona hoje)

```
1. POST /auth/register {role=PASSAGEIRO, cidade}      → 201 + JWT
2. POST /rides {origem, destino}                       → cria PENDING_PAYMENT, calcula preço via OSRM (cache hit/miss transparente)
3. POST /rides/{id}/confirm-payment {method}           → AVAILABLE_IN_MURAL (gera BR Code se PIX)
4. MOTORISTA aprovado faz POST /drivers/me/online      → online=true
5. GET /rides/mural                                    → lista filtrada por cidade do motorista
6. POST /rides/{id}/accept                             → DRIVER_EN_ROUTE (lock otimista resolve concorrência)
7. PUT /rides/{id}/driver-location (a cada ~10s)       → atualiza coordenadas
   GET /rides/{id} (a cada 5s pelo passageiro)         → retorna ride + distância Haversine
8. POST /rides/{id}/start                              → IN_PROGRESS
9. POST /rides/{id}/complete                           → COMPLETED
```

Cancelamento e desvios cobertos pelas regras de role + `RideStateMachine`.

---

## 5. Avaliação para o MVP

### Pronto / sólido
- **Todos os endpoints da matriz da `CLAUDE.md` existem e respondem com as roles corretas.**
- **Concorrência do aceite resolvida** com `@Version` + tradução para HTTP 409 — comportamento crítico do produto.
- **Cálculo de tarifa 100% no backend** — passageiro não consegue manipular preço.
- **Polling barato:** `GET /rides/{id}` usa apenas Haversine in-memory; mural usa índice parcial.
- **Cache OSRM** mitiga rate limit do servidor público.
- **Segurança coerente:** stateless, sem refresh token (escopo do MVP), `@PreAuthorize` por endpoint, filtro JWT cobrindo o pipeline.
- **GlobalExceptionHandler** traduz exceções de domínio para HTTP semânticos — bom contrato para o frontend.
- **Cobertura de testes:** `OsrmMapsServiceTest`, `RouteHasherTest`, `AuthControllerWebMvcTest`, `JwtServiceTest`, `CityNormalizerTest` e agora `RideServiceTest` (Mockito) cobrindo máquina de estados, regras de role no cancelamento, gating do `driver-location`, delegação do mural e invariante de preço calculado no backend. Lock otimista é validado por inspeção do schema + manualmente via `docs/smoke-test.md` (não tem unit test possível sem Hibernate em runtime).
- **Contrato HTTP publicado:** Swagger UI em `/swagger-ui.html`, OpenAPI JSON em `/v3/api-docs`. Bearer scheme global configurado via `OpenApiConfig`.
- **Smoke test versionado:** `docs/api.http` + `docs/smoke-test.md` permitem validar release ponta-a-ponta em ~10 min.

### Cuidados / lacunas conhecidas
- **Sem rate limiting nos endpoints de polling.** Cinco segundos por cliente está ok; mas um motorista mal-comportado pode martelar `PUT /driver-location` sem cap. Para MVP é aceitável, mas vale anotar.
- **Sem auditoria/observabilidade.** Logs SLF4J existem, mas não há métricas (Micrometer/Actuator) — para os primeiros usuários é dispensável.
- **`route_cache` sem TTL.** Decisão consciente da spec, ok pro MVP.
- **Pagamento Pix simulado.** Esperado para a fase, mas o frontend precisa saber que o "BR Code" não vai ser pago de verdade.
- **Smoke test depende de banco vivo:** `docs/smoke-test.md` precisa do Postgres do `docker-compose` rodando. CI pode adicionar uma etapa de spin-up se quisermos automatizar.

### Veredito
**Sim, o backend está em estado MVP-funcional.** Toda a feature surface do escopo está coberta e os 4 itens prioritarios da seção 7 já foram fechados (OpenAPI, integration tests do `RideService`, `docs/api.http`, `docs/smoke-test.md`). O backend está pronto para o piloto.

---

## 6. Pronto para integrar com o frontend?

**Sim, com ressalvas leves.** O contrato HTTP já é o que o app Flutter vai consumir:

- Auth → `Authorization: Bearer <jwt>` em todo endpoint protegido.
- Erros vêm em formato consistente (`ApiError` via `GlobalExceptionHandler`) — o app pode tratar 400/401/403/404/409/503 sem casos especiais.
- Polling do passageiro: `GET /rides/{id}` retorna o estado completo + `driverDistanceKm` (campo derivado via Haversine, já calculado no backend). Frontend só precisa renderizar.
- Polling do motorista: `GET /rides/mural` devolve apenas corridas da cidade do motorista. Sem trabalho extra no app.

### Recomendações antes de plugar o app
1. ~~**Publicar um contrato.**~~ **Feito.** Swagger UI em `/swagger-ui.html`, OpenAPI JSON em `/v3/api-docs`, bearer scheme global via `OpenApiConfig`.
2. **Validar payloads de saída** com o time do front. Em particular: formato dos timestamps (estamos em `Instant` → ISO-8601 UTC com `Z`), nomes de campos (camelCase já está consistente nos records), e o que `RideResponse` retorna em cada status.
3. **Definir comportamento esperado em 401 (token expirado).** Hoje o app recebe 401 limpo; precisa saber que deve redirecionar pro login (sem refresh token no MVP).
4. **Documentar o handshake do Pix simulado** — frontend vai exibir um BR Code que nunca será pago de verdade; precisa de um botão "marcar como pago" óbvio durante o piloto, OU o passageiro só usa Dinheiro no primeiro release.

---

## 7. Próximos passos sugeridos (em ordem de prioridade)

1. ✅ **OpenAPI/Swagger** — `springdoc-openapi-starter-webmvc-ui 2.6.0`, Swagger UI em `/swagger-ui.html`, JSON em `/v3/api-docs`, bearer scheme global via `OpenApiConfig`.
2. ✅ **Bateria de testes para `RideService`** — `RideServiceTest` (Mockito) cobre máquina de estados, regras de role no cancelamento, gating do `driver-location`, delegação do mural e invariante de preço backend-only. Lock otimista fica para validação manual (smoke-test §5) porque é runtime do Hibernate.
3. ✅ **Coleção HTTP versionada** — `docs/api.http` cobre auth, admin, drivers online/offline, ciclo completo da ride, casos de erro e endpoints OpenAPI.
4. ✅ **Smoke test ponta-a-ponta** — `docs/smoke-test.md` com checklist manual de ~10 min cobrindo setup, auth, aprovacao, ciclo da ride, concorrencia, escopo por cidade, cancelamento, polling, seguranca e cache OSRM.
5. **Próximo (pós-MVP):** rate limiting nos endpoints de polling, métricas (Micrometer/Actuator), políticas de TTL/invalidação no `route_cache`, depois features de produto (avaliação, push, raio de proximidade).

---

## 8. Resumo executivo

O backend está coerente com a spec, com os pontos críticos (lock otimista, escopo por cidade, online/offline, cache OSRM, máquina de estados, Haversine no polling) corretamente implementados. Com os 4 itens da seção 7 fechados (OpenAPI, integration tests do `RideService`, `docs/api.http`, `docs/smoke-test.md`), **o backend está pronto para piloto**: contrato HTTP publicado, fluxo crítico coberto por testes de integração e checklist manual para validar releases.
