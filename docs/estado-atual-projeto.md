# UniMove — Estado atual do projeto

> Snapshot em **2026-05-29**. Branch `feature/geocoding-enderecos` + histórico com **suspensão de usuário**, **compartilhamento público de viagem**, **tarifa dinâmica configurável**, **chat in-app via SSE**, **status da corrida via SSE**, **múltiplas paradas**, **geometria da rota pro mapa** e **busca de endereço (geocoding via Photon)** aplicados.
> Documento de visão geral: o que existe, como o fluxo funciona, se está pronto para MVP e para integração com o frontend.

---

## 1. Visão geral

O backend monolítico do UniMove já cobre o **fluxo ponta-a-ponta de uma corrida** — do cadastro do passageiro até a finalização pelo motorista — mais o **painel administrativo mínimo** para aprovar motoristas, suspender contas, configurar tarifa por cidade e auditar corridas. Toda a estrutura prevista na `CLAUDE.md` foi implementada: escopo por cidade, online/offline, lock otimista, cache OSRM, máquina de estados, cancelamento com regras por role e rastreamento do motorista via polling + Haversine. Sobre essa base, foram somadas funcionalidades de produto: **estimativa de preço**, **rating bidirecional**, **endereços favoritos do passageiro**, **dashboard de ganhos do motorista**, **taxa de cancelamento**, **categorias MOTO/CARRO**, **suspensão de usuário pelo ADMIN**, **compartilhamento público da viagem em tempo real**, **tarifa configurável por cidade** e **chat in-app via SSE entre passageiro e motorista**.

**Stack confirmada:** Java 21, Spring Boot 3.3.5, PostgreSQL + Flyway, JWT (jjwt 0.12.6), WebClient (OSRM), JUnit 5 + Mockito. Lombok presente.

---

## 2. Domínios implementados

### 2.1 `domain.user` — autenticação, motoristas, favoritos, admin e suspensão

- **Entidades:**
  - `User` (id, email, password_hash, name, phone, role, cidade, **rating_avg, rating_count, status, suspended_at, suspended_reason, suspended_by_admin_id**).
  - `Driver` (PK = user_id, approved, online, last_seen_at, vehicle_type, vehicle_plate).
  - `SavedPlace` (id, user_id, label, address, lat, lng, created_at) — `UNIQUE(user_id, label)`, FK `ON DELETE CASCADE`.
- **Roles:** `PASSAGEIRO`, `MOTORISTA`, `ADMIN`.
- **`UserStatus`:** `ACTIVE` | `SUSPENDED`.
- **Controllers:**
  - `AuthController` — `POST /auth/register`, `POST /auth/login` (público). Login bloqueia suspensos.
  - `DriverController` — `POST /drivers/me/online`, `POST /drivers/me/offline` (MOTORISTA).
  - `SavedPlaceController` — `POST /saved-places`, `GET /saved-places`, `DELETE /saved-places/{id}` (PASSAGEIRO).
  - `AdminDriverController` — `GET /admin/drivers/pending`, `POST /admin/drivers/{id}/approve` (ADMIN).
  - **`AdminUserController`** — `GET /admin/users/suspended`, `POST /admin/users/{id}/suspend`, `POST /admin/users/{id}/reactivate` (ADMIN).
- **Services auxiliares:** `UserRatingService` (denormaliza `rating_avg`/`rating_count` na mesma transação do POST de rating), `SavedPlaceService` (CRUD com validação de label duplicado), `DriverService.getVehicleType` (matching de categoria), **`DriverService.findPublicInfo`** (info reduzida pro share público), **`UserAccountService`** (suspensão/reativação + `requireActive` chamado em ações críticas + `findPublicInfo` pro share).
- **Exceções específicas:** `EmailAlreadyUsedException`, `InvalidCityException`, `DriverNotApprovedException`, `DriverOfflineException`, `DriverNotFoundException`, `SavedPlaceNotFoundException`, `DuplicateSavedPlaceLabelException`, **`UserSuspendedException`** (403), **`UserNotFoundException`** (404), **`CannotSuspendAdminException`** (403).
- **Validação de cidade:** `CityNormalizer` (slug normalizado).
- **JWT:** `JwtService` + `JwtAuthenticationFilter` + `AuthenticatedUser` (record de contexto com `userId`, `role`, `cidade`).
- **DTOs públicos cross-domain** (consumidos por `domain.ride` no share): `PassengerPublicInfo`, `DriverPublicInfo` — evitam importar entidade `User`/`Driver` fora do pacote.

### 2.2 `domain.ride` — núcleo do produto

- **Entidade `Ride`** com `@Version` para lock otimista, todos os timestamps separados (`accepted_at`, `started_at`, `completed_at`, `cancelled_at`), colunas de rastreamento do motorista (`driver_current_lat/lng/_updated_at`), **`category` (MOTO/CARRO)**, **`cancellation_fee`** opcional, **`share_token` UUID UNIQUE** (gerado no `@PrePersist`), **`route_geometry` TEXT** (polyline gravada no `create`) e **paradas intermediárias** em `ride_stops` (`@ElementCollection` LAZY + `@OrderColumn(seq)`, máx. 5).
- **Entidade `RideRating`** (id, ride_id, rater_id, ratee_id, score 1-5, comment, created_at) com `UNIQUE(ride_id, rater_id)` — uma avaliação por direção por corrida.
- **Entidade `PricingConfig`** (id, cidade, category, base, per_km, per_min, updated_at, updated_by_admin_id) com `UNIQUE(cidade, category)` — backing store da `PricingPolicy`.
- **Máquina de estados** `RideStateMachine` reforçando o diagrama da `CLAUDE.md`. Transição inválida → `IllegalRideTransitionException` → HTTP 409.
- **`PricingPolicy`** consulta `pricing_configs` com cache em memória (`volatile Map`, carregado no `@PostConstruct` e recarregado via `reload()` quando o ADMIN edita). Resolução: tenta `(cidade, category)`; fallback `(_DEFAULT, category)`; última rede de segurança = constantes hardcoded com WARN log. Defaults seedados em V9: CARRO `5.50/2.10/0.20`, MOTO `3.85/1.47/0.14`.
- **`CancellationPolicy`** centraliza a regra de taxa: passageiro cancelando em `DRIVER_EN_ROUTE` após **120s** do `acceptedAt` paga **R$ 3,00**. Cancelamento dentro da janela de graça, em estados anteriores, ou por motorista = grátis.
- **`RideCategory.fromVehicleType(VehicleType)`** — mapeamento 1:1. Mural e accept usam isso para casar motorista MOTO ↔ corrida MOTO.
- **`RideController` (todas as rotas previstas):**
  - `POST /rides`, `POST /rides/estimate`, `POST /rides/{id}/confirm-payment`, `POST /rides/{id}/rating` (PASSAGEIRO)
  - `GET /rides/mural`, `POST /rides/{id}/accept`, `POST /rides/{id}/start`, `POST /rides/{id}/complete` (MOTORISTA — mural agora filtra por `cidade + category`)
  - `POST /rides/{id}/cancel` (PASSAGEIRO ou MOTORISTA, com regras de estado; resposta inclui `cancellationFee` se aplicável)
  - `PUT /rides/{id}/driver-location` (MOTORISTA aceitante)
  - `GET /rides/{id}` (dono ou motorista aceitante) — usado pelo polling do app
  - `GET /rides/{id}/route` (dono ou motorista aceitante) — geometria estática da rota (`RideRouteResponse` com a polyline); buscada **uma vez** pelo app, fora do polling leve (regra 3)
  - `GET /rides/{id}/status-stream` (dono ou motorista aceitante) — SSE de status (snapshot na conexão + transições `afterCommit`, sem replay)
  - `GET /rides/history` (PASSAGEIRO ou MOTORISTA — vê apenas as próprias)
  - `POST /rides/{id}/rating` (PASSAGEIRO ou MOTORISTA, só após `COMPLETED`)
- **`SharedRideController`** (público, sem auth) — `GET /share/{token}` retorna `SharedRideResponse` com payload reduzido (sem PII). Em estado final responde **HTTP 410 GONE**.
- **`DriverEarningsController`** — `GET /drivers/me/earnings?from=&to=` (MOTORISTA), default 30 dias UTC, retorna total, média e breakdown por dia.
- **`AdminRideController`** — `GET /admin/rides` paginado (inclui category + cancellationFee).
- **`AdminPricingController`** — `GET /admin/pricing`, `PUT /admin/pricing` (upsert), `DELETE /admin/pricing?cidade=&category=` (ADMIN). Default não pode ser deletado.
- **`RideService.assertChatAllowed(user, rideId)`** — valida participante + status `DRIVER_EN_ROUTE`/`IN_PROGRESS`. Usado pelo `ChatService`.
- **`RideService.complete/cancel`** chamam `ChatSseHub.closeRide(rideId)` para encerrar conexões SSE do chat.
- **Exceções dedicadas:** `RideNotFoundException`, `RideAccessDeniedException`, `IllegalRideTransitionException`, `DriverCityMismatchException`, `MissingCancelReasonException`, `LocationUpdateNotAllowedException`, `CategoryMismatchException`, `RatingNotAllowedException`, `RatingAlreadySubmittedException`, `InvalidEarningsRangeException`, **`ShareLinkNotFoundException`** (404), **`ShareLinkExpiredException`** (410), **`PricingConfigNotFoundException`** (404), **`CannotDeleteDefaultPricingException`** (403).

### 2.3 `domain.maps` — OSRM (rotas + geometria) + Photon (geocoding)

**Gateway de rotas (OSRM):**
- `MapsService` (interface) + `OsrmMapsService` (impl) consultando `route_cache` primeiro, com fallback no OSRM público via `WebClient`.
- `RouteHasher` arredonda coordenadas a 4 casas decimais (~11 m) para gerar chave determinística. Para múltiplas paradas o hash cobre a **sequência completa de waypoints**; com 2 pontos o hash é idêntico ao formato antigo (preserva o cache existente).
- `route(List<GeoPoint>)` envia `[origem, ...paradas, destino]` num único request. OSRM chamado com `overview=full&geometries=polyline` (precisão 5).
- `RouteInfo` agora carrega `distanciaKm`, `tempoMin` **e** `geometry` (polyline). Hit no cache só conta como completo se `geometry != null`; entradas antigas (`geometry IS NULL`) disparam uma rebusca única que faz **backfill** da coluna.
- `RouteCache` é uma tabela autônoma, sem TTL — ADMIN faz `TRUNCATE` se precisar invalidar. Coluna `geometry TEXT` adicionada na V13.

**Gateway de geocoding (Photon — novo):**
- `GeocodingService` (interface) + `PhotonGeocodingService` (impl) — irmão do OSRM, base URL própria (`PHOTON_BASE_URL`), bean `photonWebClient` no `MapsConfig` com os mesmos timeouts.
- `search(query, biasLat, biasLng, limit)` → forward/autocomplete (**não cacheado**, app faz debounce ~300ms). `reverse(lat, lng)` → pin no mapa, **cacheado** em `geocode_cache` (chave = lat/lng arredondados a 4 casas, sem TTL).
- `GeoPlace` (record de saída): `displayName`, `lat`, `lng`, `street`, `city`, `state`. Parse do GeoJSON do Photon (coordenadas `[lon, lat]`).
- `GeocodingController` — `GET /maps/geocode` (autocomplete) e `GET /maps/reverse` (pin), ambos `PASSAGEIRO`/`MOTORISTA`. Geocoding **não toca** o fluxo de corrida: só produz o `lat/lng` que `POST /rides`/`/estimate` já consomem.
- `MapsUnavailableException` traduzida para HTTP 503 no `GlobalExceptionHandler` — reusada por OSRM e Photon.

### 2.4 `domain.payment` — Pix simulado

- `PaymentService` (interface) + `SimulatedPaymentService` que gera um BR Code estático/fictício. Nenhuma integração real com PSP — exatamente como previsto no MVP.

### 2.5 `domain.chat` — chat in-app via SSE (novo)

- **Entidade `ChatMessage`** (id, **seq BIGSERIAL**, ride_id, sender_id, sender_role, body 1–1000 chars, created_at). `seq` é o event ID exposto via SSE pra reconexão.
- **`ChatMessageRepository`** com `findByRideIdOrderBySeqAsc` (histórico) e `findByRideIdAndSeqGreaterThanOrderBySeqAsc` (replay no reconnect).
- **`ChatSseHub`** — `Map<rideId, CopyOnWriteArrayList<SseEmitter>>` em memória + heartbeat `@Scheduled(15s)` (evita corte por proxy idle) + `closeRide(rideId)` chamado pelo `RideService` quando a corrida termina.
- **`ChatService`** — `send`, `history`, `subscribe(lastEventId)` com replay automático antes de iniciar o live stream.
- **`ChatController`:**
  - `GET /chat/rides/{id}/messages` — histórico completo
  - `POST /chat/rides/{id}/messages` — envia mensagem
  - `GET /chat/rides/{id}/stream` — abre SSE com header `Last-Event-ID` para retomar de onde parou
- **Lifecycle:** habilitado **apenas** em `DRIVER_EN_ROUTE` e `IN_PROGRESS`. Em qualquer outro estado responde **HTTP 403** (`ChatNotAllowedException`).
- **Persistência obrigatória** — útil pra disputas e moderação. Sem TTL e sem edição/delete no MVP.

### 2.6 `shared`

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
- **`V7__user_status.sql`** — colunas `users.status` (ACTIVE/SUSPENDED), `suspended_at`, `suspended_reason`, `suspended_by_admin_id` + índice parcial `idx_users_suspended`.
- **`V8__ride_share_token.sql`** — coluna `rides.share_token` UUID UNIQUE + backfill para rides ativas.
- **`V9__pricing_configs.sql`** — tabela `pricing_configs(cidade, category, base, per_km, per_min, …)` com `UNIQUE(cidade, category)` + seed `_DEFAULT` preservando a fórmula histórica.
- **`V10__chat_messages.sql`** — tabela `chat_messages` (id, **seq BIGSERIAL**, ride_id FK, sender_id, sender_role, body 1–1000, created_at) + índice `(ride_id, seq)`.
- **`V11__ride_stops.sql`** — tabela `ride_stops` (ride_id FK, seq, lat, lng) para as paradas intermediárias (máx. 5), com `@OrderColumn(seq)`.
- **`V13__route_geometry.sql`** — coluna `route_cache.geometry TEXT` (polyline cacheada) + coluna `rides.route_geometry TEXT` (polyline da corrida; nula em rides anteriores → front cai em fallback de linha reta).
- **`V14__geocode_cache.sql`** — tabela `geocode_cache` (id, `coord_hash VARCHAR(64) UNIQUE`, display_name, street, city, state, lat/lng `NUMERIC(10,7)`, created_at) — cache do reverse geocoding.

Pontos fortes do schema:
- `version BIGINT` em `rides` viabilizando o lock otimista do aceite.
- Index parcial `idx_rides_status_cidade_cat ... WHERE status = 'AVAILABLE_IN_MURAL'` (após V6) mantém o mural barato mesmo com milhões de corridas históricas, agora também filtrando categoria.
- Index parcial `idx_users_suspended ... WHERE status = 'SUSPENDED'` (V7) entrega listagem do painel admin sem varrer a tabela inteira.
- `share_token` único com índice (V8) viabiliza lookup O(1) no endpoint público.
- `chat_messages.seq BIGSERIAL` (V10) serve como event ID monotônico do SSE — cliente sempre pode pedir "tudo depois do seq X" pra retomar.
- Todas as colunas de timestamp são `TIMESTAMPTZ`.
- `CHECK constraints` para status, payment_method, cancelled_by, vehicle_type, category, cancellation_fee, rating score, lat/lng, user_status, pricing positivos, chat body length e chat sender role — defesa em profundidade frente ao Hibernate.

---

## 4. Fluxo end-to-end (como funciona hoje)

```
1. POST /auth/register {role=PASSAGEIRO, cidade}        → 201 + JWT
1b. GET /maps/geocode?q=... (digita) | GET /maps/reverse?lat&lng (pin) → resolve endereço → lat/lng
2. POST /rides/estimate {origem, destino, stops?, category?} → preço previsto + polyline (sem persistir)
3. POST /rides {origem, destino, stops?, category?}     → cria PENDING_PAYMENT + share_token + route_geometry
4. POST /rides/{id}/confirm-payment {method}            → AVAILABLE_IN_MURAL (gera BR Code se PIX)
5. MOTORISTA aprovado faz POST /drivers/me/online       → online=true (bloqueado se suspenso)
6. GET /rides/mural                                     → lista filtrada por cidade + categoria do motorista
7. POST /rides/{id}/accept                              → DRIVER_EN_ROUTE (lock otimista + categoria + active check)
8. Passageiro/contato pode acessar GET /share/{token}   → status, motorista, placa, posição + polyline (público)
   GET /rides/{id}/route (1x) → polyline pro mapa | GET /rides/{id}/status-stream → SSE de status
   Passageiro e motorista abrem GET /chat/rides/{id}/stream → chat SSE habilitado nesta janela
9. PUT /rides/{id}/driver-location (a cada ~10s)        → atualiza coordenadas
   GET /rides/{id} (a cada 5s pelo passageiro)          → retorna ride + distância Haversine + rating do motorista
10. POST /rides/{id}/start                              → IN_PROGRESS (chat continua disponível)
11. POST /rides/{id}/complete                           → COMPLETED (SSE do chat fecha automaticamente; /share retorna 410)
12. POST /rides/{id}/rating {score, comment}            → ambos os lados (passageiro/motorista), 1 por direção
```

Cancelamento e desvios cobertos pelas regras de role + `RideStateMachine`; `CancellationPolicy` decide a taxa.
Passageiro pode usar `GET /saved-places` para popular origem/destino sem digitar. Motorista consulta `GET /drivers/me/earnings` para acompanhar faturamento.
ADMIN configura preço por cidade em `PUT /admin/pricing` e suspende contas problemáticas em `POST /admin/users/{id}/suspend`.

---

## 5. Avaliação para o MVP

### Pronto / sólido
- **Todos os endpoints da matriz da `CLAUDE.md` existem e respondem com as roles corretas.**
- **Concorrência do aceite resolvida** com `@Version` + tradução para HTTP 409 — comportamento crítico do produto.
- **Cálculo de tarifa 100% no backend** — passageiro não consegue manipular preço; resolução agora via `pricing_configs(cidade, category)` com cache em memória + fallback `_DEFAULT` + última rede hardcoded.
- **Polling barato:** `GET /rides/{id}` usa apenas Haversine in-memory; mural usa índice parcial `(status, cidade, category)`.
- **Cache OSRM** mitiga rate limit do servidor público.
- **Estimativa pré-corrida** (`POST /rides/estimate`) usa o mesmo gateway/cache OSRM, sem persistir, dando previsibilidade ao passageiro.
- **Rating bidirecional pós-`COMPLETED`** com `UNIQUE(ride_id, rater_id)` e denormalização de `rating_avg`/`rating_count` na mesma transação — `GET /rides/{id}` já entrega a média do motorista, sem JOIN no polling.
- **Endereços favoritos** (`POST/GET/DELETE /saved-places`) reduzem fricção da criação da corrida.
- **Dashboard de ganhos** (`GET /drivers/me/earnings`) agrega corridas `COMPLETED` por dia com query nativa Postgres, default 30 dias UTC.
- **Taxa de cancelamento** centralizada em `CancellationPolicy` (R$ 3,00 após 120s do accept) — passageiro paga, motorista não; ADMIN vê o valor em `/admin/rides`.
- **Matching por categoria** — motorista MOTO só vê/aceita corridas MOTO; tentativa cross-category → HTTP 409 (`CategoryMismatchException`).
- **Suspensão de usuário pelo ADMIN** — `POST /admin/users/{id}/suspend|reactivate`. Enforcement **assimétrico**: bloqueia login + ações críticas (create/accept/online) mas não onera polling. Conta ADMIN não pode ser suspensa.
- **Compartilhamento público da viagem** — `GET /share/{token}` (sem auth) entrega payload mínimo (sem PII) com posição em tempo real do motorista. Estado final → HTTP 410.
- **Tarifa configurável por cidade** — ADMIN edita via `PUT /admin/pricing`. `PricingPolicy` mantém cache em memória recarregado a cada upsert; `_DEFAULT` protegido contra deleção.
- **Chat in-app via SSE** — `GET /chat/rides/{id}/stream` (SSE) + `POST .../messages` + `GET .../messages` (histórico). Habilitado apenas em `DRIVER_EN_ROUTE`/`IN_PROGRESS`. Persistente em `chat_messages` com `seq BIGSERIAL` para reconexão via `Last-Event-ID`. Heartbeat 15s + `closeRide()` no fim da corrida.
- **Segurança coerente:** stateless, sem refresh token (escopo do MVP), `@PreAuthorize` por endpoint, filtro JWT cobrindo o pipeline. `/share/**` é o único endpoint público fora de `/auth/**`.
- **GlobalExceptionHandler** traduz exceções de domínio para HTTP semânticos — bom contrato para o frontend.
- **Cobertura de testes (55, sem Docker/Postgres):** `OsrmMapsServiceTest` (cache hit/miss + backfill de geometria + polyline), `PhotonGeocodingServiceTest` (forward + bias, reverse cache hit/miss, Photon 5xx → 503), `RouteHasherTest`, `AuthControllerWebMvcTest`, `JwtServiceTest`, `CityNormalizerTest` e `RideServiceTest` (Mockito) cobrindo máquina de estados, regras de role no cancelamento, gating do `driver-location`, delegação do mural, paradas, geometria da rota e invariante de preço calculado no backend. Lock otimista é validado por inspeção do schema + manualmente via `docs/smoke-test.md` (não tem unit test possível sem Hibernate em runtime).
- **Contrato HTTP publicado:** Swagger UI em `/swagger-ui.html`, OpenAPI JSON em `/v3/api-docs`. Bearer scheme global configurado via `OpenApiConfig`.
- **Smoke test versionado:** `docs/api.http` + `docs/smoke-test.md` permitem validar release ponta-a-ponta em ~10 min.

### Cuidados / lacunas conhecidas
- **Suspensão tem janela de até 24h pra usuários já logados.** Bloqueia login e ações de escrita imediatamente, mas o token JWT existente continua válido em GETs até expirar. Trade-off consciente — adicionar SELECT por request mataria o polling. Quando virar problema, reduzir TTL do JWT ou usar revocation list.
- **Hub SSE do chat e cache do pricing são por instância.** Funciona perfeito num único nó; quando rodar com múltiplas réplicas, ambos precisam virar pub/sub (Redis ou Postgres `LISTEN/NOTIFY`).
- **Sem rate limiting nos endpoints de polling/chat.** Cinco segundos por cliente está ok; mas usuário mal-comportado pode martelar `PUT /driver-location` ou `POST /chat/.../messages` sem cap. Para MVP é aceitável, mas vale anotar.
- **Sem auditoria/observabilidade.** Logs SLF4J existem, mas não há métricas (Micrometer/Actuator) — para os primeiros usuários é dispensável.
- **`route_cache` sem TTL.** Decisão consciente da spec, ok pro MVP.
- **Pagamento Pix simulado.** Esperado para a fase, mas o frontend precisa saber que o "BR Code" não vai ser pago de verdade. A `cancellation_fee` também é apenas registrada — a cobrança em si segue manual no piloto.
- **Sem cobrança automática da taxa de cancelamento.** O valor fica gravado na ride; cobrar do passageiro depende do fluxo financeiro real (depois do MVP).
- **Sem strike pra motorista que cancela.** Hoje fica apenas o registro em `cancelled_by` + `cancel_reason`. Painel de strikes é feature pós-MVP.
- **Categorias limitadas a MOTO/CARRO.** COMFORT/COMFORT XL exigem flag de elegibilidade no motorista — adiado.
- **Chat sem edição/delete/anexo/typing indicator.** Texto puro 1–1000 chars. Suficiente pra coordenar embarque ("estou na esquina X"). Anexo/foto exige storage + moderação.
- **Smoke test depende de banco vivo:** `docs/smoke-test.md` precisa do Postgres do `docker-compose` rodando. CI pode adicionar uma etapa de spin-up se quisermos automatizar.

### Veredito
**Sim, o backend está em estado MVP-funcional++.** Além do escopo original, já entraram features que normalmente esperariam o pós-MVP (estimativa, rating bi, favoritos, earnings, taxa de cancelamento, categorias) **e agora também**: suspensão por ADMIN, compartilhamento público em tempo real, tarifa configurável por cidade e chat in-app via SSE. Os 4 itens prioritários da seção 7 estão fechados (OpenAPI, integration tests do `RideService`, `docs/api.http`, `docs/smoke-test.md`). O backend está pronto para o piloto com diferenciais reais pra cidade de pequeno porte.

---

## 6. Pronto para integrar com o frontend?

**Sim, com ressalvas leves.** O contrato HTTP já é o que o app Flutter vai consumir:

- Auth → `Authorization: Bearer <jwt>` em todo endpoint protegido.
- Erros vêm em formato consistente (`ApiError` via `GlobalExceptionHandler`) — o app pode tratar 400/401/403/404/409/503 sem casos especiais.
- Polling do passageiro: `GET /rides/{id}` retorna o estado completo + `driverDistanceKm` (Haversine) + `motoristaRatingAvg/Count` + `category` + `cancellationFee` (quando aplicável). Frontend só precisa renderizar.
- Polling do motorista: `GET /rides/mural` devolve apenas corridas da cidade **e categoria** do motorista — zero trabalho de filtragem no app.
- UX auxiliares prontas: `POST /rides/estimate` para preview de preço antes de criar; `GET /saved-places` para popular endereços salvos; `POST /rides/{id}/rating` no fim do fluxo; `GET /drivers/me/earnings` para dashboard financeiro do motorista.
- **Chat em tempo real:** app abre `GET /chat/rides/{id}/stream` (SSE) quando entra em `DRIVER_EN_ROUTE`, manda mensagens via `POST .../messages` e reconecta usando `Last-Event-ID` se a rede cair. Spring entrega o transporte; nenhum broker extra precisa rodar.
- **Compartilhamento público:** `share_token` vem em todo `RideResponse` (gerado no `@PrePersist`); app gera o link `https://unimove.com/share/{token}` e o passageiro envia pra família. Endpoint não exige auth e expira sozinho ao fim da corrida.
- **Mapa "estilo Uber":** tiles + render são responsabilidade do app (`flutter_map` + OSM). O backend entrega a **polyline** (Encoded Polyline, precisão 5, compatível com `flutter_polyline_points`): no `POST /rides/estimate` (preview), no `GET /rides/{id}/route` (uma vez, fora do polling) e no `SharedRideResponse`. Linha estática — não entra no polling de 5s. Guia detalhado em `docs/plano-mapa-mvp.md`.
- **Busca de endereço:** o app resolve texto↔coordenada via `GET /maps/geocode` (autocomplete, debounce ~300ms no app) e `GET /maps/reverse` (pin arrastável, cacheado no backend). O `lat/lng` escolhido alimenta os mesmos campos de `estimate`/`create`. Guia em `docs/plano-busca-endereco.md`.

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
6. ✅ **Suspensão de usuário, compartilhamento público de viagem, tarifa configurável por cidade, chat in-app via SSE, status da corrida via SSE, múltiplas paradas, geometria da rota pro mapa (polyline) e busca de endereço (geocoding via Photon).**
7. **Próximas features de produto:**
   - **Denúncia + bloqueio mútuo** — passageiro/motorista denuncia o outro; mural filtra corridas de bloqueados. Combina bem com a suspensão já existente.
   - **Código de embarque (4 dígitos)** — gerado em `accept`, validado no `start` — evita "entrei no carro errado".
   - **Corrida com espera ("ida e volta")** — diferencial real pra cidade pequena (mercado, consulta).
   - **Código de indicação** — `referral_code` em `users` + crédito ao indicador na 1ª corrida do indicado.
   - **Comissão da plataforma** — coluna `platform_fee` calculada no `complete` → completa a história financeira do motorista (já tem gross em `/earnings`, falta net).
   - **Strike de motorista que cancela** — agregação em cima de `cancelled_by = MOTORISTA`; bloqueio temporário do mural.
   - **Heatmap básico de demanda** — agregação SQL sobre `rides` por bairro (lat/lng arredondado).
8. **Pós-MVP (infra):** rate limiting nos endpoints de polling/chat, métricas (Micrometer/Actuator), políticas de TTL/invalidação no `route_cache`, cobrança automática da `cancellation_fee` quando entrar gateway real, refresh token, pub/sub do `ChatSseHub` e cache do `PricingPolicy` pra escalar horizontalmente.

---

## 8. Resumo executivo

O backend está coerente com a spec, com os pontos críticos (lock otimista, escopo por cidade, online/offline, cache OSRM, máquina de estados, Haversine no polling) corretamente implementados — e somou camada de produto que normalmente fica pra depois: estimativa, rating bi, favoritos, earnings, taxa de cancelamento, categorias MOTO/CARRO, **suspensão de usuário**, **compartilhamento público em tempo real**, **tarifa configurável por cidade** e **chat in-app via SSE**. Com os 4 itens da seção 7 fechados (OpenAPI, integration tests do `RideService`, `docs/api.http`, `docs/smoke-test.md`), **o backend está pronto para piloto**: contrato HTTP publicado, fluxo crítico coberto por testes, novas features matando fricção real do app (chat sem expor telefone, link público pra família acompanhar, ADMIN com controle real de preço e contas) e checklist manual para validar releases.
