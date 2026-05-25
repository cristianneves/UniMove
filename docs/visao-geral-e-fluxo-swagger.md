# UniMove — Visão geral, escolhas de MVP e fluxo no Swagger

> Documento de referência para quem chega no projeto agora. Explica **o que é o UniMove**, **por que cada escolha de MVP foi feita** (com o paralelo do que Uber/99pop fazem), e fornece um **roteiro completo** para validar o backend ponta-a-ponta no Swagger UI.

---

## 1. O que é o UniMove

App de mobilidade urbana (caronas + mototáxis) **focado em cidades de pequeno e médio porte** onde os gigantes (Uber, 99pop) operam mal ou simplesmente não operam. O backend deste repositório é um **monolito modular em Spring Boot 3.3** que sustenta o MVP com o menor custo de infraestrutura possível.

A meta do MVP **não é competir feature-a-feature com Uber**, é **validar o modelo de negócio** numa cidade-piloto. Cada decisão técnica abaixo foi tomada sob essa lente: o que é o mínimo viável para um motorista pegar uma corrida, levar o passageiro, receber e ser avaliado — sem incinerar dinheiro em infraestrutura.

---

## 2. Arquitetura em uma página

```
Flutter app  ─HTTP/JSON─────►  Spring Boot (monolito)
   ▲                              │
   │ short polling 5s             ├── domain.user     (auth, motoristas, favoritos, admin, suspensão)
   │                              ├── domain.ride     (mural, FSM, pricing dinâmico, rating, cancelamento,
   │ SSE p/ chat in-app           │                    ganhos, share público da viagem)
   │                              ├── domain.chat     (chat in-app via SSE entre passageiro e motorista)
Browser/zap ─GET /share/{token}─► ├── domain.maps     (gateway OSRM + cache local)
   (sem auth)                     ├── domain.payment  (Pix/Dinheiro simulado)
                                  └── shared          (security, exception handler, utils)
                                  │
                  PostgreSQL ◄────┘
                       │
                       ├── route_cache       (hits antes de bater no OSRM público)
                       ├── pricing_configs   (tarifa por cidade/categoria, editável pelo ADMIN)
                       └── chat_messages     (histórico do chat com seq BIGSERIAL)
                              │
                              └── OSRM público (router.project-osrm.org) — fallback no miss
```

Cada `domain.X` expõe **só interfaces/DTOs**. Nenhum domínio importa entidade JPA de outro — `RideService` fala com motoristas via `DriverService`, com mapas via `MapsService`, com pagamento via `PaymentService`. `ChatService` fala com `RideService.assertChatAllowed` e o `SharedRideController` compõe sua resposta via `UserAccountService.findPublicInfo` + `DriverService.findPublicInfo` (DTOs públicos). Isso permite quebrar em microserviços depois sem reescrita.

---

## 3. Decisões de MVP — o **porquê** de cada uma

> A coluna "Uber/99pop" é uma aproximação; o ponto não é descrever a implementação real deles, é mostrar **a complexidade que estamos abrindo mão de propósito**.

### 3.1 Disparo por **mural global por cidade** — não por raio de proximidade

| | UniMove (MVP) | Uber/99pop |
|---|---|---|
| Quem vê a corrida | Todos os motoristas online da mesma cidade | Motorista mais próximo recebe push individual |
| Critério de match | Ordem de clique no botão "aceitar" | Score (distância + ETA + rating + tempo ocioso) |
| Infra exigida | 1 endpoint `GET /rides/mural` + lock otimista | Cluster geoespacial (Redis GEO/H3), websockets, ranking, push provider |

**Por quê:** disparo por raio exige rastrear posição de todos os motoristas online em tempo real, indexar geograficamente, calcular ETA, ranquear, decidir critério de desempate e ainda mandar push. Para uma cidade-piloto com 20-50 motoristas online ao mesmo tempo, **o mural global resolve o mesmo problema com 1% da complexidade**. Se a base crescer ao ponto de motoristas reclamarem de "corrida longe demais", a evolução natural é segmentar por bairro — mas sem isso virar prioridade artificial agora.

### 3.2 Escopo por **cidade** desde o V1 — não é multi-tenant nem global

Toda `User` e toda `Ride` carregam o campo `cidade` (slug normalizado, ex.: `"sao-jose-do-rio-preto"`). O mural filtra **sempre** por `WHERE status = AVAILABLE_IN_MURAL AND cidade = :cidadeDoMotorista`.

**Por quê:** o produto nasce para conquistar **uma cidade de cada vez**. Mesmo no MVP, deixar o campo `cidade` desde o início custa zero hoje e evita uma migration dolorosa quando abrirmos a segunda cidade. Uber resolve isso com bounding boxes regionais e roteamento por região — para nós, um string column resolve.

### 3.3 **HTTP short polling para estado da corrida**, não WebSockets

App Flutter bate em `GET /rides/{id}` a cada ~5s (passageiro acompanhando a corrida) e `GET /rides/mural` a cada ~5s (motorista escolhendo corrida). Sem WebSockets para o ciclo da corrida.

**Por quê:** WebSocket exige stack assíncrona, gestão de conexão, reconexão, autenticação por handshake, balanceador sticky, monitoramento dedicado. Para 50 motoristas online + 200 corridas/dia, **polling é trivialmente barato** num único nó Spring Boot. O custo é latência de até 5s para ver mudança de estado — irrelevante na UX de uma corrida que dura 15-30 min.

**Exceção: chat in-app usa SSE (não polling, não WebSocket).** Polling de 5s pra chat dá péssima UX; WebSocket é overkill pra mensagens de texto. SSE entrega latência baixa (servidor empurra a mensagem assim que persiste), JWT funciona pelo header, reconexão é nativa via `Last-Event-ID`. Ver §3.13.

### 3.4 Rastreamento do motorista por **PUT + Haversine**, não streaming GPS

Entre `DRIVER_EN_ROUTE` e `IN_PROGRESS`, o app do motorista chama `PUT /rides/{id}/driver-location` (lat/lng) a cada ~10s. O endpoint persiste em `Ride.driver_current_lat/lng/_updated_at`. No polling do passageiro (`GET /rides/{id}`), a distância em linha reta motorista↔origem é calculada por **Haversine no backend** — sem chamar OSRM nesse polling (overhead seria absurdo).

**Por quê:** o passageiro só precisa saber "motorista está a ~1.2km de você". Roteamento curva-a-curva exige chamar OSRM a cada polling, com cache de rota dinâmica, projeção em via — caro e desnecessário num MVP. Fórmula Haversine é 5 linhas e estima bem o suficiente em distâncias urbanas curtas.

### 3.5 **Cache local de rotas OSRM** (`route_cache`)

Antes de chamar `router.project-osrm.org`, o `MapsService` consulta uma tabela `route_cache` indexada por hash determinístico de `(latO, lngO, latD, lngD)` arredondados a **4 casas decimais** (~11m de precisão). Hit → retorna direto. Miss → chama OSRM, persiste, retorna.

**Por quê:** o OSRM público **não tem SLA** e tem rate limit. Em cidades pequenas, a mesma rota (centro → bairros, faculdade → rodoviária) se repete o tempo todo. Cache evita gargalo externo e funciona como circuit breaker natural. Sem TTL no MVP — se uma rua mudar significativamente, ADMIN faz `TRUNCATE route_cache`. Aceitamos imprecisão de tempo por trânsito porque o cálculo é **orçamentário**, não GPS em tempo real.

Próximo passo natural (não agora): self-host OSRM via Docker.

### 3.6 **Motorista marcado por categoria no User**, não por veículo dinâmico

`Driver` tem `vehicle_type` (`MOTO` ou `CARRO`) e `vehicle_plate`. A corrida tem `category` (`MOTO`/`CARRO`). Mural e accept filtram por `category = motorista.vehicleType`.

**Por quê:** Uber lida com motoristas que mudam de carro, alugam veículos, têm múltiplas categorias (UberX, Comfort, Black, Moto). Para o MVP, **um motorista = um veículo = uma categoria**. Se o cara mudar de carro, ADMIN edita. Se quiser virar mototaxista, vira motorista de novo. Simplifica o modelo a um custo de produto desprezível na cidade-piloto.

### 3.7 **Pagamento simulado** (Pix + Dinheiro)

`PaymentService.confirmPayment(...)` muda a Ride de `PENDING_PAYMENT` para `AVAILABLE_IN_MURAL` sem falar com nenhum PSP. Pix gera um BR Code estático fictício. Dinheiro é só uma flag.

**Por quê:** integração real com Mercado Pago/PagSeguro/etc. exige KYC, conta PJ, webhooks, conciliação, antifraude. **Validar o modelo de negócio não depende disso** — depende de passageiros e motoristas usarem o app. Quando o produto provar tração, plugamos um PSP real no `PaymentService` (interface já existe).

### 3.8 **Cadastro de motorista sem documentos** — aprovação manual por ADMIN

Sem upload de CNH/CRLV, sem validação automática. Motorista se cadastra, fica com `approved = false`, e ADMIN aprova manualmente em `POST /admin/drivers/{id}/approve`.

**Por quê:** validação documental exige OCR, antifraude facial, integração com Detran/Serpro. **Na cidade-piloto, o operador conhece os motoristas pessoalmente** (ou via indicação). Aprovação manual via painel é mais barata, mais rápida e mais segura num MVP.

### 3.9 **JWT stateless sem refresh token**

Access token único de 24h. Sem refresh, sem blacklist, sem sessão no banco.

**Por quê:** refresh token traz toda uma stack (rotação, revogação, blacklist em Redis). Para MVP, 24h é suficiente — usuário relogou, pegou token novo. Quando a base for grande o bastante para justificar segurança fina-granular (revogar sessão de dispositivo perdido, etc.), adicionamos.

### 3.10 **Lock otimista** no aceite do mural

`Ride` tem `@Version`. Dois motoristas tentando aceitar a mesma corrida → o primeiro `save()` vence; o segundo recebe `ObjectOptimisticLockingFailureException` → HTTP **409** `"Esta corrida já foi aceita por outro motorista."`

**Por quê:** modelagem do mural exige resolver corrida disputada. Lock pessimista trava transação e degrada throughput. Lock otimista resolve **na própria camada de persistência** com zero infra extra. Falha é um caminho normal de fluxo, tratada pelo `GlobalExceptionHandler`.

### 3.11 **Rating denormalizado em `users`**

Tabela `ride_ratings` guarda cada avaliação, mas `users.rating_avg` e `users.rating_count` são atualizados na **mesma transação** do POST de rating.

**Por quê:** o app mostra estrelas em todo lugar (no mural, no polling, no histórico). Calcular `AVG()` + `COUNT()` em cada polling seria absurdo. Denormalizar custa 2 colunas e uma atualização extra por rating — ganho de performance é gigantesco.

### 3.12 **Taxa de cancelamento** com janela de graça de 120s

Passageiro cancelando em `DRIVER_EN_ROUTE` após 120s do `acceptedAt` paga **R$ 3,00**. Antes disso, ou nos estados anteriores, é grátis. Motorista cancelando é sempre grátis (com justificativa obrigatória).

**Por quê:** sem taxa, passageiro chama e cancela sem custo — motorista perde tempo dirigindo à toa. Janela de graça evita punir desistência rápida. R$ 3,00 é simbólico mas suficiente para criar disciplina. Cobrança real é responsabilidade do gateway (que não existe no MVP) — hoje o valor só fica registrado na `Ride.cancellation_fee`.

### 3.13 **Chat in-app via SSE** — não WhatsApp, não WebSocket

Entre `DRIVER_EN_ROUTE` e `IN_PROGRESS`, passageiro e motorista trocam mensagens via:
- `GET /chat/rides/{id}/stream` — abre SSE, servidor empurra mensagens em tempo real
- `POST /chat/rides/{id}/messages` — envio (HTTP normal)
- `GET /chat/rides/{id}/messages` — histórico

Mensagens persistem em `chat_messages` com `seq BIGSERIAL`; cliente reconecta mandando header `Last-Event-ID: <seq>` e recebe replay automático. `ChatSseHub` mantém emitters em memória + heartbeat de 15s. Quando a ride vira `COMPLETED`/`CANCELLED`, `RideService` chama `ChatSseHub.closeRide` — todas as conexões caem com evento `closed`.

**Por quê não WhatsApp:** expõe telefone das duas partes. Em cidade pequena isso vira problema na hora (assédio, chamadas fora do horário, dump na lista de contatos). Chat dentro do app preserva privacidade e fica gravado pra disputa.

**Por quê SSE e não WebSocket:** chat é praticamente unidirecional (server→client; envio é POST). WebSocket exigiria handshake JWT, sub-protocol, reconexão manual, broker. SSE custa ~80 linhas de Java, JWT funciona pelo header, reconexão é nativa. Quando precisar de typing/read-receipt em tempo real, dá pra adicionar via eventos nomeados no mesmo SSE.

### 3.14 **Suspensão de usuário pelo ADMIN** com enforcement assimétrico

`POST /admin/users/{id}/suspend` muda `users.status` para `SUSPENDED` (com `suspended_at`, `suspended_reason`, `suspended_by_admin_id`). Reativação via `POST /admin/users/{id}/reactivate`. Conta ADMIN não pode ser suspensa.

**Enforcement deliberadamente assimétrico:**
- ✅ Login bloqueado (HTTP 403)
- ✅ Ações de escrita (`POST /rides`, `POST /rides/{id}/accept`, `POST /drivers/me/online`) bloqueadas via `UserAccountService.requireActive`
- ❌ GETs (polling de mural, ride detail) **não** validam status

**Por quê:** adicionar `SELECT FROM users WHERE id=?` no filtro JWT dobraria o custo de cada poll de 5s. Suspenso já não consegue criar/aceitar nada nem voltar a logar — o "rastro" de leitura por até 24h (TTL do JWT) é aceitável pro MVP. Quando virar problema, reduz TTL do token ou implementa revogação.

### 3.15 **Compartilhamento público da viagem** com link "expira sozinho"

Toda `Ride` ganha um `share_token` UUID gerado no `@PrePersist`. `GET /share/{token}` (público, sem auth) retorna `SharedRideResponse` com status, coordenadas, primeiro nome e placa do motorista, rating, posição em tempo real e distância haversine. **Não expõe** ID da ride, telefone, email, preço, payload Pix. Em estado final (`COMPLETED`/`CANCELLED`) responde **HTTP 410 GONE** — link "expira" naturalmente.

**Por quê:** segurança vende. Passageiro manda o link no zap pra mãe, mãe acompanha em tempo real sem instalar nada. Custo no backend: 0 (reusa `driver_current_lat/lng` que já existe). Sem TTL adicional porque a janela útil é curta (~30 min) e o token é difícil de adivinhar.

### 3.16 **Tarifa configurável via ADMIN**, não hardcoded

A fórmula `preco = base + per_km * km + per_min * min` continua a mesma, mas os coeficientes vêm da tabela `pricing_configs(cidade, category, base, per_km, per_min)`. `PricingPolicy` mantém cache em memória (`volatile Map`) carregado no startup; ADMIN edita via `PUT /admin/pricing` e o service invalida o cache. Fallback: `(cidade) → (_DEFAULT) → constantes hardcoded de segurança` — app nunca quebra. `_DEFAULT` é protegido contra deleção.

**Por quê:** preço é a alavanca mais importante de teste do produto. Travar a fórmula em código exigiria deploy pra ajustar. Com a tabela, ADMIN testa "MOTO 20% mais barato no piloto de Catanduva" em segundos. Quando rodar com múltiplas instâncias, o `reload()` precisará virar evento pub/sub (Redis ou Postgres `LISTEN`).

---

## 4. O que ficou de fora do MVP (e por quê)

| Feature | Razão de ficar de fora |
|---|---|
| Upload e validação automática de CNH/CRLV | Custo de integração + custo de OCR/antifraude. Aprovação manual resolve. |
| Mapa síncrono curva-a-curva | OSRM público + cache + Haversine cobrem a UX necessária. |
| Gateway de pagamento real | Validação de modelo não depende disso. |
| Disparo por raio + push individual | Mural global resolve com 1% da complexidade. |
| WebSockets | Short polling cobre o ciclo da corrida; SSE cobre o chat. |
| Refresh tokens | Token de 24h é suficiente. |
| Multi-veículo por motorista | Cidade-piloto não precisa. |
| Promoções, cupons, programa de fidelidade | Foco em validar o fluxo core. |
| Typing indicator / read receipts no chat | Texto puro é suficiente pra coordenar embarque. |
| Edição/delete de mensagem | Mantém histórico inalterado pra disputa. |
| Anexos/fotos no chat | Exige storage + moderação. |

---

## 5. Fluxo completo no Swagger UI

> Subir a app com `mvn spring-boot:run` e abrir **http://localhost:8080/swagger-ui.html**. Todos os endpoints protegidos exigem o header `Authorization: Bearer <token>` — no Swagger, clicar no cadeado e colar `Bearer <token>` (ou só `<token>`, depende da config). Use o botão **Authorize** no topo.

### 5.1 Cenário

- 1 passageiro (`ana@test.com`)
- 1 motorista MOTO já aprovado (`bruno@test.com`)
- 1 admin (vem seedado em `V2__seed_admin.sql`)
- Cidade: `sao-jose-do-rio-preto`

### 5.2 Roteiro passo-a-passo

#### Passo 1 — Registrar passageiro
**`POST /auth/register`**
```json
{
  "email": "ana@test.com",
  "password": "senha123",
  "name": "Ana Silva",
  "phone": "+5517999990001",
  "cidade": "São José do Rio Preto",
  "role": "PASSAGEIRO"
}
```
✅ Retorna `AuthResponse` com `token`. **Guarde** este token como `TOKEN_ANA`.

#### Passo 2 — Registrar motorista
**`POST /auth/register`**
```json
{
  "email": "bruno@test.com",
  "password": "senha123",
  "name": "Bruno Motorista",
  "phone": "+5517999990002",
  "cidade": "São José do Rio Preto",
  "role": "MOTORISTA",
  "vehicleType": "MOTO",
  "vehiclePlate": "ABC1D23"
}
```
✅ Retorna token. **Guarde** como `TOKEN_BRUNO`. Mas o motorista ainda está `approved=false` — não pode aceitar corrida.

#### Passo 3 — Login do ADMIN
**`POST /auth/login`** (credenciais do seed; veja `V2__seed_admin.sql`)
```json
{ "email": "admin@unimove.com", "password": "..." }
```
✅ Guarde como `TOKEN_ADMIN`.

#### Passo 4 — Admin lista motoristas pendentes e aprova o Bruno
**`Authorize`** com `TOKEN_ADMIN`.

**`GET /admin/drivers/pending`** → retorna array com o Bruno e o `userId`.

**`POST /admin/drivers/{id}/approve`** com o `userId` do Bruno.
✅ Bruno agora tem `approved=true`.

#### Passo 5 — Motorista fica online
**`Authorize`** com `TOKEN_BRUNO`.

**`POST /drivers/me/online`**
✅ `online=true`, `last_seen_at` atualizado. Já pode ver o mural.

#### Passo 6 — Passageiro estima preço (opcional, mas é o fluxo real do app)
**`Authorize`** com `TOKEN_ANA`.

**`POST /rides/estimate`**
```json
{
  "latOrigem": -20.8113,
  "lngOrigem": -49.3758,
  "latDestino": -20.7950,
  "lngDestino": -49.4050,
  "category": "MOTO"
}
```
✅ Retorna `{ "distanciaKm": ..., "tempoMin": ..., "preco": ... }`. Primeira chamada vai no OSRM; chamadas subsequentes com mesmo par de coordenadas batem no `route_cache`.

#### Passo 7 — Passageiro cria a corrida
**`POST /rides`**
```json
{
  "latOrigem": -20.8113,
  "lngOrigem": -49.3758,
  "latDestino": -20.7950,
  "lngDestino": -49.4050,
  "category": "MOTO",
  "paymentMethod": "PIX"
}
```
✅ Retorna `RideResponse` com `status: "PENDING_PAYMENT"` e um `shareToken`. **Guarde** o `id` como `RIDE_ID` e o `shareToken` como `SHARE_TOKEN`.

#### Passo 8 — Passageiro confirma pagamento (Pix simulado)
**`POST /rides/{RIDE_ID}/confirm-payment`**
```json
{ "paymentMethod": "PIX" }
```
✅ Status muda para `AVAILABLE_IN_MURAL`. Corrida agora aparece no mural do Bruno.

#### Passo 9 — Motorista vê o mural
**`Authorize`** com `TOKEN_BRUNO`.

**`GET /rides/mural`**
✅ Lista com a corrida da Ana (filtrada por `cidade + category`).

#### Passo 10 — Motorista aceita
**`POST /rides/{RIDE_ID}/accept`**
✅ Status → `DRIVER_EN_ROUTE`. Se outro motorista tentar aceitar, recebe **409** (lock otimista).

#### Passo 11 — Motorista atualiza localização (simulando deslocamento)
**`PUT /rides/{RIDE_ID}/driver-location`**
```json
{ "lat": -20.8090, "lng": -49.3780 }
```
✅ Passageiro chamando `GET /rides/{RIDE_ID}` agora vê `driverCurrentLat/Lng` + distância Haversine.

#### Passo 11.1 — (Familiar) acompanha a viagem via link público
Em uma aba **anônima** (sem token), abrir `GET /share/{SHARE_TOKEN}`.
✅ Retorna payload reduzido sem PII: status, coordenadas, primeiro nome do motorista, placa, rating, posição atual e distância até o próximo ponto. Endpoint não exige `Authorization`.

#### Passo 11.2 — Passageiro e motorista trocam mensagens (chat SSE)
Como a corrida está em `DRIVER_EN_ROUTE`, o chat está habilitado.

**Passageiro envia:** `POST /chat/rides/{RIDE_ID}/messages` (com `TOKEN_ANA`)
```json
{ "body": "Estou no portão azul." }
```
✅ Retorna `ChatMessageResponse` com `seq: 1` (gerado pela BIGSERIAL).

**Motorista lê histórico:** `GET /chat/rides/{RIDE_ID}/messages` (com `TOKEN_BRUNO`)
✅ Lista com a mensagem da Ana.

**Motorista assina o stream** (Swagger não renderiza SSE bem — use `curl` no terminal):
```bash
curl -N -H "Authorization: Bearer $TOKEN_BRUNO" \
     http://localhost:8080/chat/rides/$RIDE_ID/stream
```
✅ Conexão fica aberta. Cada nova mensagem chega no formato `event: message\nid: <seq>\ndata: {...}\n\n`. Servidor envia heartbeat `:ping` a cada 15s.

**Motorista responde:** `POST /chat/rides/{RIDE_ID}/messages` com `{"body": "Chegando em 2 min."}` — o `curl` do passageiro recebe instantaneamente.

**Reconexão:** se o stream cair, reabrir com header `Last-Event-ID: <último seq visto>` → servidor reentrega o que faltou antes de continuar live.

#### Passo 12 — Motorista inicia viagem
**`POST /rides/{RIDE_ID}/start`**
✅ Status → `IN_PROGRESS`. Chat continua aberto. `/share/{SHARE_TOKEN}` continua acessível.

#### Passo 13 — Motorista finaliza
**`POST /rides/{RIDE_ID}/complete`**
✅ Status → `COMPLETED`. `completed_at` preenchido. Streams SSE do chat recebem evento `closed` e fecham. `GET /share/{SHARE_TOKEN}` agora responde **HTTP 410 GONE**.

#### Passo 14 — Avaliações bidirecionais
**Passageiro avalia motorista** (`Authorize` com `TOKEN_ANA`):

**`POST /rides/{RIDE_ID}/rating`**
```json
{ "score": 5, "comment": "Motorista educado." }
```

**Motorista avalia passageiro** (`Authorize` com `TOKEN_BRUNO`):

**`POST /rides/{RIDE_ID}/rating`**
```json
{ "score": 5 }
```
✅ Direção é inferida pela role do chamador. `users.rating_avg` e `rating_count` atualizam na mesma transação. Tentar avaliar duas vezes → **409**.

#### Passo 15 — Histórico e ganhos
- **`GET /rides/history`** (com qualquer dos dois tokens) → lista paginada das próprias corridas.
- **`GET /drivers/me/earnings?from=2026-05-01&to=2026-05-31`** (Bruno) → agregado de ganhos com breakdown diário.

#### Passo 16 — (ADMIN) operações de painel
**`Authorize`** com `TOKEN_ADMIN`.

- **Configurar tarifa específica para SJRP:** `PUT /admin/pricing`
  ```json
  { "cidade": "sao-jose-do-rio-preto", "category": "MOTO",
    "base": 4.00, "perKm": 1.80, "perMin": 0.15 }
  ```
  ✅ Próxima `POST /rides/estimate` em SJRP usará esses valores; outras cidades caem no `_DEFAULT`.

- **Listar todas as configs:** `GET /admin/pricing` → 2 do `_DEFAULT` + a nova de SJRP.

- **Reverter:** `DELETE /admin/pricing?cidade=sao-jose-do-rio-preto&category=MOTO` → SJRP volta a usar `_DEFAULT`.

- **Suspender um passageiro problemático:** `POST /admin/users/{ID_DA_ANA}/suspend`
  ```json
  { "reason": "Reclamações reiteradas de motoristas; cancelou 8 corridas em 1h." }
  ```
  ✅ Próximo `POST /auth/login` da Ana → 403 `UserSuspendedException`. Token antigo da Ana ainda consegue GET por até 24h, mas não consegue criar/aceitar nada.

- **Reativar:** `POST /admin/users/{ID_DA_ANA}/reactivate`.

### 5.3 Cenários alternativos para testar

| Cenário | Como reproduzir | Resultado esperado |
|---|---|---|
| Aceite simultâneo | Abrir 2 abas com 2 tokens MOTORISTA diferentes e dar `accept` quase ao mesmo tempo | 1º → 200, 2º → 409 |
| Cancelamento dentro da graça | Passageiro cria, paga, motorista aceita, passageiro cancela em <120s | `cancellation_fee = 0` |
| Cancelamento fora da graça | Mesmo cenário, mas espera >120s | `cancellation_fee = 3.00` |
| Motorista offline tenta aceitar | `POST /drivers/me/offline`, depois `accept` | 403/409 conforme regra |
| Motorista CARRO tenta aceitar corrida MOTO | Cadastrar motorista CARRO, criar corrida MOTO | Corrida nem aparece no mural; accept direto → 409/403 |
| Cidade diferente | Cadastrar motorista em outra cidade | Mural vazio para corridas de SJRP |
| Chat fora da janela | `POST /chat/rides/{id}/messages` em ride `PENDING_PAYMENT` ou `COMPLETED` | 403 `ChatNotAllowedException` |
| Chat de não-participante | Terceiro usuário tenta `GET /chat/rides/{id}/messages` | 403 `RideAccessDeniedException` |
| Body de chat acima de 1000 chars | `POST .../messages` com `body` longo | 400 (Bean Validation) |
| Share de ride encerrada | `GET /share/{token}` após `complete` | 410 GONE |
| Share token inexistente | `GET /share/{uuid-aleatório}` | 404 |
| Suspenso tenta logar | `POST /auth/login` com user em `status=SUSPENDED` | 403 `UserSuspendedException` |
| Suspender ADMIN | `POST /admin/users/{id-admin}/suspend` | 403 `CannotSuspendAdminException` |
| Deletar pricing `_DEFAULT` | `DELETE /admin/pricing?cidade=_DEFAULT&category=CARRO` | 403 `CannotDeleteDefaultPricingException` |
| Pricing por cidade override | PUT em SJRP, depois `/rides/estimate` em SJRP vs outra cidade | Preços diferem |

---

## 6. Resumo de uma linha

**UniMove troca complexidade técnica por foco em mercado:** mural global ao invés de match geoespacial, polling ao invés de WebSockets, SSE ao invés de WebSocket pro chat, pagamento simulado ao invés de PSP real, aprovação manual ao invés de OCR, suspensão assimétrica ao invés de revogação por request, tarifa em tabela ao invés de surge dinâmico — tudo isso para subir o produto numa cidade-piloto com infra mínima e iterar a partir do uso real, sem queimar capital tentando ser Uber no dia 1. As features que **sobraram** (share público, chat in-app sem expor telefone, ADMIN com controle real de preço e contas) são exatamente as que cidade pequena percebe como diferencial.
