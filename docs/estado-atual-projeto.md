# UniMove — Estado atual do projeto

> Snapshot em **2026-05-21**. Branch `main`, último commit `36b21a8` + working tree com saved places, earnings, taxa de cancelamento e categorias MOTO/CARRO aplicados.
> Documento de visão geral: o que existe, como o fluxo funciona, se está pronto para MVP e para integração com o frontend.

---

## 1. Visão geral

O backend monolítico do UniMove já cobre o **fluxo ponta-a-ponta de uma corrida** — do cadastro do passageiro até a finalização pelo motorista — mais o **painel administrativo mínimo** para aprovar motoristas e auditar corridas. Toda a estrutura prevista na `CLAUDE.md` foi implementada: escopo por cidade, online/offline, lock otimista, cache OSRM, máquina de estados, cancelamento com regras por role e rastreamento do motorista via polling + Haversine. Sobre essa base, foram somadas funcionalidades de produto: **estimativa de preço**, **rating bidirecional**, **endereços favoritos do passageiro**, **dashboard de ganhos do motorista**, **taxa de cancelamento** e **categorias MOTO/CARRO**.

**Stack confirmada:** Java 21, Spring Boot 3.3.5, PostgreSQL + Flyway, JWT (jjwt 0.12.6), WebClient (OSRM), JUnit 5 + Mockito. Lombok presente.

---

## 2. Domínios implementados

### 2.1 `domain.user` — autenticação, motoristas, favoritos e admin

- **Entidades:**
  - `User` (id, email, password_hash, name, phone, role, cidade, **rating_avg, rating_count**).
  - `Driver` (PK = user_id, approved, online, last_seen_at, vehicle_type, vehicle_plate).
  - `SavedPlace` (id, user_id, label, address, lat, lng, created_at) — `UNIQUE(user_id, label)`, FK `ON DELETE CASCADE`.
- **Roles:** `PASSAGEIRO`, `MOTORISTA`, `ADMIN`.
- **Controllers:**
  - `AuthController` — `POST /auth/register`, `POST /auth/login` (público).
  - `DriverController` — `POST /drivers/me/online`, `POST /drivers/me/offline` (MOTORISTA).
  - `SavedPlaceController` — `POST /saved-places`, `GET /saved-places`, `DELETE /saved-places/{id}` (PASSAGEIRO).
  - `AdminDriverController` — `GET /admin/drivers/pending`, `POST /admin/drivers/{id}/approve` (ADMIN).
- **Services auxiliares:** `UserRatingService` (denormaliza `rating_avg`/`rating_count` na mesma transação do POST de rating), `SavedPlaceService` (CRUD com validação de label duplicado), `DriverService.getVehicleType` (usado pelo matching de categoria no mural/accept).
- **Exceções específicas:** `EmailAlreadyUsedException`, `InvalidCityException`, `DriverNotApprovedException`, `DriverOfflineException`, `DriverNotFoundException`, `SavedPlaceNotFoundException`, `DuplicateSavedPlaceLabelException`.
- **Validação de cidade:** `CityNormalizer` (slug normalizado).
- **JWT:** `JwtService` + `JwtAuthenticationFilter` + `AuthenticatedUser` (record de contexto com `userId`, `role`, `cidade`).

### 2.2 `domain.ride` — núcleo do produto

- **Entidade `Ride`** com `@Version` para lock otimista, todos os timestamps separados (`accepted_at`, `started_at`, `completed_at`, `cancelled_at`), colunas de rastreamento do motorista (`driver_current_lat/lng/_updated_at`), **`category` (MOTO/CARRO)** e **`cancellation_fee`** opcional.
- **Entidade `RideRating`** (id, ride_id, rater_id, ratee_id, score 1-5, comment, created_at) com `UNIQUE(ride_id, rater_id)` — uma avaliação por direção por corrida.
- **Máquina de estados** `RideStateMachine` reforçando o diagrama da `CLAUDE.md`. Transição inválida → `IllegalRideTransitionException` → HTTP 409.
- **`PricingPolicy`** aplica `5.50 + 2.10*km + 0.20*min` com `BigDecimal` e multiplica por categoria: MOTO = `0.7x`, CARRO = `1.0x`.
- **`CancellationPolicy`** centraliza a regra de taxa: passageiro cancelando em `DRIVER_EN_ROUTE` após **120s** do `acceptedAt` paga **R$ 3,00**. Cancelamento dentro da janela de graça, em estados anteriores, ou por motorista = grátis.
- **`RideCategory.fromVehicleType(VehicleType)`** — mapeamento 1:1. Mural e accept usam isso para casar motorista MOTO ↔ corrida MOTO.
- **`RideController` (todas as rotas previstas):**
  - `POST /rides`, `POST /rides/estimate`, `POST /rides/{id}/confirm-payment`, `POST /rides/{id}/rating` (PASSAGEIRO)
  - `GET /rides/mural`, `POST /rides/{id}/accept`, `POST /rides/{id}/start`, `POST /rides/{id}/complete` (MOTORISTA — mural agora filtra por `cidade + category`)
  - `POST /rides/{id}/cancel` (PASSAGEIRO ou MOTORISTA, com regras de estado; resposta inclui `cancellationFee` se aplicável)
  - `PUT /rides/{id}/driver-location` (MOTORISTA aceitante)
  - `GET /rides/{id}` (dono ou motorista aceitante) — usado pelo polling do app
  - `GET /rides/history` (PASSAGEIRO ou MOTORISTA — vê apenas as próprias)
  - `POST /rides/{id}/rating` (PASSAGEIRO ou MOTORISTA, só após `COMPLETED`)
- **`DriverEarningsController`** — `GET /drivers/me/earnings?from=&to=` (MOTORISTA), default 30 dias UTC, retorna total, média e breakdown por dia.
- **`AdminRideController`** — `GET /admin/rides` paginado (inclui category + cancellationFee).
- **Exceções dedicadas:** `RideNotFoundException`, `RideAccessDeniedException`, `IllegalRideTransitionException`, `DriverCityMismatchException`, `MissingCancelReasonException`, `LocationUpdateNotAllowedException`, `CategoryMismatchException`, `RatingNotAllowedException`, `RatingAlreadySubmittedException`, `InvalidEarningsRangeException`.

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

- `V1__init_schema.sql` — cria `users`, `drivers`, `rides`, `route_cache` com índices certos para as queries críticas.
- `V2__seed_admin.sql` — usuário admin inicial com hash BCrypt.
- `V3__ride_ratings.sql` — tabela `ride_ratings` (rating bidirecional) + colunas `rating_avg` / `rating_count` denormalizadas em `users`.
- `V4__saved_places.sql` — tabela `saved_places` com `UNIQUE(user_id, label)` e FK `ON DELETE CASCADE`.
- `V5__ride_cancellation_fee.sql` — coluna `rides.cancellation_fee` (NUMERIC nullable, check `>= 0`).
- `V6__ride_category.sql` — coluna `rides.category` (MOTO/CARRO, NOT NULL, check) + reescreve o índice do mural para `(status, cidade, category)`.

Pontos fortes do schema:
- `version BIGINT` em `rides` viabilizando o lock otimista do aceite.
- Index parcial `idx_rides_status_cidade_cat ... WHERE status = 'AVAILABLE_IN_MURAL'` (após V6) mantém o mural barato mesmo com milhões de corridas históricas, agora também filtrando categoria.
- Todas as colunas de timestamp são `TIMESTAMPTZ`.
- `CHECK constraints` para status, payment_method, cancelled_by, vehicle_type, category, cancellation_fee, rating score e lat/lng — defesa em profundidade frente ao Hibernate.

---

## 4. Fluxo end-to-end (como funciona hoje)

```
1. POST /auth/register {role=PASSAGEIRO, cidade}        → 201 + JWT
2. POST /rides/estimate {origem, destino, category?}    → preço previsto (sem persistir)
3. POST /rides {origem, destino, category?}             → cria PENDING_PAYMENT, calcula preço final via OSRM
4. POST /rides/{id}/confirm-payment {method}            → AVAILABLE_IN_MURAL (gera BR Code se PIX)
5. MOTORISTA aprovado faz POST /drivers/me/online       → online=true
6. GET /rides/mural                                     → lista filtrada por cidade + categoria do motorista
7. POST /rides/{id}/accept                              → DRIVER_EN_ROUTE (lock otimista + check de categoria)
8. PUT /rides/{id}/driver-location (a cada ~10s)        → atualiza coordenadas
   GET /rides/{id} (a cada 5s pelo passageiro)          → retorna ride + distância Haversine + rating do motorista
9. POST /rides/{id}/start                               → IN_PROGRESS
10. POST /rides/{id}/complete                           → COMPLETED
11. POST /rides/{id}/rating {score, comment}            → ambos os lados (passageiro/motorista), 1 por direção
```

Cancelamento e desvios cobertos pelas regras de role + `RideStateMachine`; `CancellationPolicy` decide a taxa.
Passageiro pode usar `GET /saved-places` para popular origem/destino sem digitar. Motorista consulta `GET /drivers/me/earnings` para acompanhar faturamento.

---

## 5. Avaliação para o MVP

### Pronto / sólido
- **Todos os endpoints da matriz da `CLAUDE.md` existem e respondem com as roles corretas.**
- **Concorrência do aceite resolvida** com `@Version` + tradução para HTTP 409 — comportamento crítico do produto.
- **Cálculo de tarifa 100% no backend** — passageiro não consegue manipular preço; multiplicador de categoria aplicado no `PricingPolicy`.
- **Polling barato:** `GET /rides/{id}` usa apenas Haversine in-memory; mural usa índice parcial `(status, cidade, category)`.
- **Cache OSRM** mitiga rate limit do servidor público.
- **Estimativa pré-corrida** (`POST /rides/estimate`) usa o mesmo gateway/cache OSRM, sem persistir, dando previsibilidade ao passageiro.
- **Rating bidirecional pós-`COMPLETED`** com `UNIQUE(ride_id, rater_id)` e denormalização de `rating_avg`/`rating_count` na mesma transação — `GET /rides/{id}` já entrega a média do motorista, sem JOIN no polling.
- **Endereços favoritos** (`POST/GET/DELETE /saved-places`) reduzem fricção da criação da corrida.
- **Dashboard de ganhos** (`GET /drivers/me/earnings`) agrega corridas `COMPLETED` por dia com query nativa Postgres, default 30 dias UTC.
- **Taxa de cancelamento** centralizada em `CancellationPolicy` (R$ 3,00 após 120s do accept) — passageiro paga, motorista não; ADMIN vê o valor em `/admin/rides`.
- **Matching por categoria** — motorista MOTO só vê/aceita corridas MOTO; tentativa cross-category → HTTP 409 (`CategoryMismatchException`).
- **Segurança coerente:** stateless, sem refresh token (escopo do MVP), `@PreAuthorize` por endpoint, filtro JWT cobrindo o pipeline.
- **GlobalExceptionHandler** traduz exceções de domínio para HTTP semânticos — bom contrato para o frontend.
- **Cobertura de testes:** `OsrmMapsServiceTest`, `RouteHasherTest`, `AuthControllerWebMvcTest`, `JwtServiceTest`, `CityNormalizerTest` e `RideServiceTest` (Mockito, 29 cenários) cobrindo máquina de estados, regras de role no cancelamento, gating do `driver-location`, delegação do mural e invariante de preço calculado no backend. Lock otimista é validado por inspeção do schema + manualmente via `docs/smoke-test.md` (não tem unit test possível sem Hibernate em runtime).
- **Contrato HTTP publicado:** Swagger UI em `/swagger-ui.html`, OpenAPI JSON em `/v3/api-docs`. Bearer scheme global configurado via `OpenApiConfig`.
- **Smoke test versionado:** `docs/api.http` + `docs/smoke-test.md` permitem validar release ponta-a-ponta em ~10 min.

### Cuidados / lacunas conhecidas
- **Sem rate limiting nos endpoints de polling.** Cinco segundos por cliente está ok; mas um motorista mal-comportado pode martelar `PUT /driver-location` sem cap. Para MVP é aceitável, mas vale anotar.
- **Sem auditoria/observabilidade.** Logs SLF4J existem, mas não há métricas (Micrometer/Actuator) — para os primeiros usuários é dispensável.
- **`route_cache` sem TTL.** Decisão consciente da spec, ok pro MVP.
- **Pagamento Pix simulado.** Esperado para a fase, mas o frontend precisa saber que o "BR Code" não vai ser pago de verdade. A `cancellation_fee` também é apenas registrada — a cobrança em si segue manual no piloto.
- **Sem cobrança automática da taxa de cancelamento.** O valor fica gravado na ride; cobrar do passageiro depende do fluxo financeiro real (depois do MVP).
- **Sem strike pra motorista que cancela.** Hoje fica apenas o registro em `cancelled_by` + `cancel_reason`. Painel de strikes é feature pós-MVP.
- **Categorias limitadas a MOTO/CARRO.** COMFORT/COMFORT XL exigem flag de elegibilidade no motorista — adiado.
- **Smoke test depende de banco vivo:** `docs/smoke-test.md` precisa do Postgres do `docker-compose` rodando. CI pode adicionar uma etapa de spin-up se quisermos automatizar.

### Veredito
**Sim, o backend está em estado MVP-funcional+.** Além do escopo original, já entraram features que normalmente esperariam o pós-MVP (estimativa, rating bi, favoritos, earnings, taxa de cancelamento, categorias). Os 4 itens prioritários da seção 7 estão fechados (OpenAPI, integration tests do `RideService`, `docs/api.http`, `docs/smoke-test.md`). O backend está pronto para o piloto.

---

## 6. Pronto para integrar com o frontend?

**Sim, com ressalvas leves.** O contrato HTTP já é o que o app Flutter vai consumir:

- Auth → `Authorization: Bearer <jwt>` em todo endpoint protegido.
- Erros vêm em formato consistente (`ApiError` via `GlobalExceptionHandler`) — o app pode tratar 400/401/403/404/409/503 sem casos especiais.
- Polling do passageiro: `GET /rides/{id}` retorna o estado completo + `driverDistanceKm` (Haversine) + `motoristaRatingAvg/Count` + `category` + `cancellationFee` (quando aplicável). Frontend só precisa renderizar.
- Polling do motorista: `GET /rides/mural` devolve apenas corridas da cidade **e categoria** do motorista — zero trabalho de filtragem no app.
- UX auxiliares prontas: `POST /rides/estimate` para preview de preço antes de criar; `GET /saved-places` para popular endereços salvos; `POST /rides/{id}/rating` no fim do fluxo; `GET /drivers/me/earnings` para dashboard financeiro do motorista.

### Recomendações antes de plugar o app
1. ~~**Publicar um contrato.**~~ **Feito.** Swagger UI em `/swagger-ui.html`, OpenAPI JSON em `/v3/api-docs`, bearer scheme global via `OpenApiConfig`.
2. **Validar payloads de saída** com o time do front. Em particular: formato dos timestamps (estamos em `Instant` → ISO-8601 UTC com `Z`), nomes de campos (camelCase já está consistente nos records), e o que `RideResponse` retorna em cada status.
3. **Definir comportamento esperado em 401 (token expirado).** Hoje o app recebe 401 limpo; precisa saber que deve redirecionar pro login (sem refresh token no MVP).
4. **Documentar o handshake do Pix simulado** — frontend vai exibir um BR Code que nunca será pago de verdade; precisa de um botão "marcar como pago" óbvio durante o piloto, OU o passageiro só usa Dinheiro no primeiro release.

---

## 7. Próximos passos sugeridos (em ordem de prioridade)

1. ✅ **OpenAPI/Swagger** — `springdoc-openapi-starter-webmvc-ui 2.6.0`, Swagger UI em `/swagger-ui.html`, JSON em `/v3/api-docs`, bearer scheme global via `OpenApiConfig`.
2. ✅ **Bateria de testes para `RideService`** — `RideServiceTest` (Mockito) cobre máquina de estados, regras de role no cancelamento, gating do `driver-location`, delegação do mural (cidade + categoria) e invariante de preço backend-only.
3. ✅ **Coleção HTTP versionada** — `docs/api.http`.
4. ✅ **Smoke test ponta-a-ponta** — `docs/smoke-test.md`.
5. ✅ **Estimativa de preço, rating bidirecional, favoritos, earnings, taxa de cancelamento, categorias MOTO/CARRO.**
6. **Próximas features de produto:**
   - **Cupom de desconto / código promocional** — tabela `coupons`, aplicação em estimate + create.
   - **Programa de indicação** — `referral_code` em `users` + crédito ao indicador na 1ª corrida do indicado.
   - **Comissão da plataforma** — coluna `platform_fee` calculada no `complete` → completa a história financeira do motorista (já tem gross em `/earnings`, falta net).
   - **Strike de motorista que cancela** — agregação em cima de `cancelled_by = MOTORISTA`; bloqueio temporário do mural.
   - **Surge pricing simples** — multiplicador por cidade/horário em tabela config, ADMIN seta janelas.
7. **Pós-MVP (infra):** rate limiting nos endpoints de polling, métricas (Micrometer/Actuator), políticas de TTL/invalidação no `route_cache`, cobrança automática da `cancellation_fee` quando entrar gateway real, refresh token.

---

## 8. Resumo executivo

O backend está coerente com a spec, com os pontos críticos (lock otimista, escopo por cidade, online/offline, cache OSRM, máquina de estados, Haversine no polling) corretamente implementados — e somou camada de produto que normalmente fica pra depois (estimativa, rating bi, favoritos, earnings, taxa de cancelamento, categorias MOTO/CARRO). Com os 4 itens da seção 7 fechados (OpenAPI, integration tests do `RideService`, `docs/api.http`, `docs/smoke-test.md`), **o backend está pronto para piloto**: contrato HTTP publicado, fluxo crítico coberto por testes, novas features matando fricção real do app e checklist manual para validar releases.
