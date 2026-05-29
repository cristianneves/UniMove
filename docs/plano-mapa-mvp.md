# Guia de Integração — Mapa "estilo Uber" no app (dev mobile + agente de IA)

> **Para quem é este documento:** o desenvolvedor do app Flutter da UniMove e o
> agente de IA que o auxilia. Ele descreve **como consumir o backend** para
> renderizar o mapa com a rota desenhada, marcadores e o pin do motorista se
> movendo — tudo **grátis** e **sem navegação turn-by-turn** (fora do escopo do MVP).
>
> **Status do backend:** ✅ pronto (branch `feature/mapa-geometria-rota`, migration
> `V13`, regra 19 do `CLAUDE.md`). Todos os endpoints abaixo já existem e estão
> testados. Nada mais precisa mudar no servidor para o app desenhar o mapa.

---

## 1. Visão geral — divisão de responsabilidades

O "mapa" são 3 camadas. O backend cobre a 2; o app cobre a 1; a 3 não existe no MVP.

| Camada | Onde vive | Como |
|--------|-----------|------|
| **1. Render + tiles** (desenhar o mapa, marcadores, linha) | **App Flutter** | `flutter_map` + tiles OpenStreetMap |
| **2. Routing** (rota, distância, tempo, **geometria/polyline**) | **Backend** | OSRM — já exposto via API |
| **3. Navegação turn-by-turn** (voz, "vire à direita") | ❌ não existe | fora de escopo (camada paga dos apps reais) |

**O que o backend entrega para o mapa:**
- A **polyline** (linha do trajeto) — o app decodifica e desenha por cima dos tiles.
- As **coordenadas** de origem, destino e paradas — viram marcadores.
- A **posição do motorista** (atualizada via polling) + distância em linha reta — vira o pin que se move.
- As **transições de estado** em tempo quase real (SSE) — para a UI reagir (aceitou, chegou, iniciou, finalizou).

**O que o app faz:** desenha os tiles, decodifica a polyline, posiciona os marcadores
e move o pin. Nenhum cálculo de rota/preço no app (regra 2 do backend).

---

## 2. Autenticação (vale para todos os endpoints, exceto `/share`)

Todas as chamadas autenticadas vão com o JWT no header:

```
Authorization: Bearer <access_token>
```

O token vem de `POST /auth/login`. É stateless, validade 24h, sem refresh no MVP.
Em 401, redirecionar para login. O endpoint `GET /share/{token}` é **público** (não
manda Authorization).

**Convenções de payload:**
- Coordenadas e valores monetários são **números decimais** (no JSON chegam como número/string decimal). No app, parsear com cuidado (use `num`/`Decimal`, não arredonde lat/lng).
- Timestamps são **ISO-8601 UTC** (ex: `2026-05-28T18:30:00Z`).
- `category`: `CARRO` ou `MOTO`. `paymentMethod`: `PIX` ou `DINHEIRO`.
- `status` da corrida: `PENDING_PAYMENT`, `AVAILABLE_IN_MURAL`, `DRIVER_EN_ROUTE`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `EXPIRED`.

---

## 3. A peça-chave do mapa: a polyline

A linha da rota é uma **polyline codificada** (Encoded Polyline Algorithm, **precisão 5**
— o padrão do Google/OSRM). No Flutter, decodifique com `flutter_polyline_points`:

```dart
import 'package:flutter_polyline_points/flutter_polyline_points.dart';

List<LatLng> decodeRoute(String encoded) {
  final result = PolylinePoints().decodePolyline(encoded); // precisão 5 (default)
  return result.map((p) => LatLng(p.latitude, p.longitude)).toList();
}
```

> ⚠️ A polyline pode vir **`null`** em corridas criadas antes desta feature (V13).
> Sempre trate o `null` com um **fallback de linha reta** entre origem e destino
> (`Polyline(points: [origem, destino])`). Corridas novas sempre têm geometria.

A geometria é **estática** durante a corrida (origem/paradas/destino não mudam).
**Busque uma vez e guarde** — não rebusque a cada poll.

---

## 4. Endpoints que o app usa para o mapa

### 4.1 `POST /rides/estimate` — preview da rota antes de criar (role `PASSAGEIRO`)
Use na tela de "confirmar corrida" para já desenhar a rota e mostrar preço/tempo.

**Request:**
```json
{
  "latOrigem": -20.81972, "lngOrigem": -49.37944,
  "latDestino": -20.79500, "lngDestino": -49.36000,
  "category": "CARRO",
  "stops": [ { "lat": -20.80500, "lng": -49.37000 } ]
}
```
`category` e `stops` são opcionais (`stops` máx. 5).

**Response (`EstimateResponse`):**
```json
{ "distanciaKm": 5.0, "tempoMin": 12, "preco": 18.40, "geometry": "}_p~F~ps|U..." }
```
→ Decodifique `geometry` e desenhe a rota. Mostre `preco`/`tempoMin`.

### 4.2 `POST /rides` — cria a corrida (role `PASSAGEIRO`)
Mesmo body do estimate. Retorna `RideResponse` (status inicial `PENDING_PAYMENT`).
Em seguida o passageiro confirma o pagamento:

`POST /rides/{id}/confirm-payment` → body `{ "method": "PIX" }` ou `{ "method": "DINHEIRO" }`
→ status vai para `AVAILABLE_IN_MURAL`.

### 4.3 `GET /rides/{id}/route` — a polyline da corrida criada ⭐ (role participante)
**É o endpoint dedicado ao mapa.** Chame **uma única vez** ao abrir a tela da corrida
(passageiro ou motorista). Leve e cacheável no app.

**Response (`RideRouteResponse`):**
```json
{ "geometry": "}_p~F~ps|U..." }
```
→ Decodifique e desenhe a linha. Não chame de novo durante a corrida.

Acesso: passageiro dono ou motorista aceitante; qualquer outro → 403.

### 4.4 `GET /rides/{id}` — polling de estado + posição do motorista (role participante)
**Chame a cada ~5s** enquanto a tela da corrida está aberta. É a **fonte de verdade**.
**Não traz** a polyline de propósito (para o poll ser leve) — a linha você já pegou no 4.3.

**Campos relevantes para o mapa (`RideResponse`):**
```json
{
  "id": "...", "status": "DRIVER_EN_ROUTE",
  "latOrigem": -20.81972, "lngOrigem": -49.37944,
  "latDestino": -20.79500, "lngDestino": -49.36000,
  "stops": [ { "lat": -20.80500, "lng": -49.37000 } ],
  "driverCurrentLat": -20.81000, "driverCurrentLng": -49.37500,
  "driverLocationUpdatedAt": "2026-05-28T18:31:10Z",
  "driverDistanceKm": 1.2,
  "category": "CARRO", "preco": 18.40,
  "motoristaRatingAvg": 4.8, "motoristaRatingCount": 132
}
```
→ Marcadores fixos: origem, destino, paradas. Pin móvel: `driverCurrentLat/Lng`
(pode ser `null` antes do motorista enviar a 1ª posição). Texto "Motorista a
`driverDistanceKm` km" (linha reta — Haversine; é estimativa, não GPS curva-a-curva).

> **Semântica de `driverDistanceKm`:** em `DRIVER_EN_ROUTE` é a distância do motorista
> até a **origem** (ele está vindo te buscar); em `IN_PROGRESS` é até o **destino final**.

### 4.5 `GET /rides/{id}/status-stream` — transições em tempo real (SSE, role participante)
Opcional, mas é o que dá a sensação "Uber" (reage na hora, sem esperar o poll de 5s).
**Não substitui** o polling 4.4 — use os dois: SSE para reagir rápido a mudança de
status, polling para a posição do motorista.

- `Content-Type: text/event-stream`. Header `Authorization: Bearer` normalmente.
- Ao conectar, o servidor manda **imediatamente um snapshot** do estado atual — então
  reconexão é trivial (não precisa de `Last-Event-ID`).
- Eventos:
  - `event: status` → `data` é um `RideStatusEvent`:
    ```json
    { "rideId": "...", "status": "IN_PROGRESS", "at": "2026-05-28T18:35:00Z",
      "terminal": false, "cancelledBy": null, "cancelReason": null }
    ```
  - `event: closed` (`data: "ride-ended"`) → vem **depois** de um status terminal
    (`COMPLETED`/`CANCELLED`/`EXPIRED`); o servidor fecha a conexão em seguida. Não reconecte.
  - Linhas de comentário `:ping` a cada 15s (heartbeat) — ignore.
- Quando `terminal: true`, atualize a UI para o estado final e pare de fazer polling.

Cliente sugerido no Flutter: pacote `sse_client` ou um `http` stream lendo linhas
`event:`/`data:`. (O backend usa o mesmo padrão SSE do chat — regra 16.)

### 4.6 `PUT /rides/{id}/driver-location` — **app do MOTORISTA** envia a posição
Só no app do motorista, enquanto a corrida está em `DRIVER_EN_ROUTE` ou `IN_PROGRESS`.
**Chame a cada ~10s** com o GPS do aparelho:
```json
{ "lat": -20.81000, "lng": -49.37500 }
```
É isso que faz o pin se mover no app do passageiro (via 4.4). O passageiro **não** chama este endpoint.

### 4.6.1 `GET /maps/geocode` e `GET /maps/reverse` — definir os pontos (busca de endereço)
Como o usuário escolhe origem/destino/paradas (estilo Uber/99). Detalhes completos em
[`plano-busca-endereco.md`](./plano-busca-endereco.md). Resumo:

- **Digitar e achar (autocomplete):** `GET /maps/geocode?q=<texto>&lat=<bias>&lng=<bias>&limit=5`
  → `List<GeoPlace>` para um dropdown. `lat/lng` opcionais = centro do mapa/GPS (enviesa o
  resultado pra perto). **Faça debounce de ~300ms** no campo de busca (só dispara quando o
  usuário para de digitar) — protege o provedor e evita requisição a cada tecla.
- **Arrastar o pin:** `GET /maps/reverse?lat=<lat>&lng=<lng>` → um `GeoPlace` com o endereço
  do ponto, pra confirmar ("Av. Brasil, 1200 — Centro").
- `GeoPlace`: `{ "displayName", "lat", "lng", "street", "city", "state" }`. O `lat/lng`
  escolhido alimenta os mesmos campos de `POST /rides/estimate` e `POST /rides`.

### 4.7 `GET /share/{token}` — página pública de acompanhamento (sem auth)
Para um terceiro (família) acompanhar pelo link do WhatsApp. Retorna `SharedRideResponse`
com `geometry`, coordenadas, `stops`, nome/placa/rating do motorista e
`driverCurrentLat/Lng` — **sem** dados sensíveis (id, telefone, preço, Pix).
Em estado final responde **HTTP 410 GONE** (o link "expira" sozinho).

---

## 5. Como montar cada tela

### App do PASSAGEIRO
1. **Confirmar corrida:** `POST /rides/estimate` → desenha rota (`geometry`) + mostra preço/tempo.
2. **Criar + pagar:** `POST /rides` → `POST /rides/{id}/confirm-payment`.
3. **Acompanhar:** ao abrir a tela da corrida:
   - 1× `GET /rides/{id}/route` → desenha a linha (guarda em memória).
   - Abre o SSE `GET /rides/{id}/status-stream` → reage a mudanças de status.
   - Polling `GET /rides/{id}` a cada 5s → move o pin do motorista + atualiza "a X km".
   - Marcadores: origem, destino, paradas (de `latOrigem/...`, `stops`).
4. **Fim:** evento `terminal` (ou status `COMPLETED`/`CANCELLED`) → fecha SSE, para o polling, vai pra tela de avaliação.

### App do MOTORISTA
- Mesma tela de mapa (passos do `route` + polling + SSE), **mais**:
- A cada ~10s: `PUT /rides/{id}/driver-location` com o GPS → alimenta o pin do passageiro.
- O motorista vê a própria rota desenhada e os marcadores; a navegação curva-a-curva
  fica por conta de um app externo (Google Maps/Waze) se ele quiser — **não é a UniMove**.

---

## 6. Pacotes Flutter sugeridos (todos grátis)

| Item | Pacote | Observação |
|------|--------|------------|
| Mapa + tiles | `flutter_map` | tiles OSM no MVP |
| Decodificar polyline | `flutter_polyline_points` | precisão 5 (default) |
| Marcadores / linha | nativo do `flutter_map` (`MarkerLayer`, `PolylineLayer`) | — |
| SSE (status em tempo real) | `sse_client` ou `http` + parse manual | opcional |

Esboço de tela (pseudo-Flutter):
```dart
FlutterMap(
  options: MapOptions(initialCenter: origem, initialZoom: 14),
  children: [
    TileLayer(urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'),
    PolylineLayer(polylines: [
      Polyline(points: routePoints, strokeWidth: 4, color: Colors.black),
    ]),
    MarkerLayer(markers: [
      Marker(point: origem, child: const Icon(Icons.trip_origin)),
      ...stops.map((s) => Marker(point: s, child: const Icon(Icons.circle))),
      Marker(point: destino, child: const Icon(Icons.flag)),
      if (driverPos != null)
        Marker(point: driverPos, child: const Icon(Icons.local_taxi)),
    ]),
  ],
)
```

> ⚠️ **Política de tiles OSM:** o tile server público do OpenStreetMap tem
> [usage policy](https://operations.osmfoundation.org/policies/tiles/) e **não** serve
> apps em produção/escala (precisa User-Agent identificável e proíbe alto volume).
> Para o MVP/testes serve. Ao crescer, troque a `urlTemplate` por um provedor com free
> tier (MapTiler/Stadia/Protomaps) ou self-host de tiles. **Isso é decisão/config do
> app — não muda nada no backend.**

---

## 7. Resumo dos endpoints (cola rápida)

| Endpoint | Método | Quando o app chama | Para o mapa |
|----------|--------|--------------------|-------------|
| `/rides/estimate` | POST | tela de confirmação (1×) | rota preview + preço |
| `/rides` + `/rides/{id}/confirm-payment` | POST | criar corrida | — |
| `/rides/{id}/route` | GET | abrir corrida (**1×**) | **polyline da rota** |
| `/rides/{id}` | GET | **polling 5s** | pin do motorista + status |
| `/rides/{id}/status-stream` | GET (SSE) | abrir corrida (stream) | reagir a transições |
| `/rides/{id}/driver-location` | PUT | **só motorista, a cada 10s** | alimenta o pin |
| `/share/{token}` | GET (público) | link compartilhado | acompanhamento externo |

---

## 8. Fora de escopo (não esperar do backend)

- **Turn-by-turn / navegação por voz** — não existe; é a camada paga dos apps reais.
- **Recálculo de rota se o motorista desviar** — a linha é o trajeto orçamentário (mesma
  filosofia do cache de rotas, regra 11). Ela não se "ajusta" ao caminho real do motorista.
- **Posição do motorista por push/WebSocket** — é via polling de 5s (regra 10). O SSE
  empurra **status**, não localização.
- **Tiles pagos / mapa proprietário** — usar OSM grátis; trocar de provedor é config do app.
