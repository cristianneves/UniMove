# Plano — Mapa "estilo Uber" (grátis, sem turn-by-turn) — MVP

> **Status:** ✅ Backend implementado (branch `feature/mapa-geometria-rota`, migration
> `V13`, regra 19 no CLAUDE.md). Falta apenas a parte do app Flutter (seção 4), que é
> fora deste repositório. Resumo do que entrou no backend na seção 5.

> Objetivo: deixar o app **visualmente parecido com Uber/99** — mapa com a rota
> desenhada, marcadores de origem/destino/paradas e o pin do motorista se movendo —
> **sem custo de licença** e **sem navegação curva-a-curva** (fora do escopo do MVP,
> ver CLAUDE.md). Reaproveita o que já existe: OSRM, `route_cache`, polling da
> localização (regra 10) e SSE de status (regra 18).

---

## 1. O que é frontend e o que é backend

O "mapa" são 3 camadas. Só uma é paga nos apps reais — e é a que **não** vamos fazer.

| Camada | Onde vive | Solução grátis | Status no MVP |
|--------|-----------|----------------|---------------|
| **1. Render + tiles** (desenhar o mapa) | **Flutter** | `flutter_map` + tiles OpenStreetMap | App (fora deste repo) |
| **2. Routing** (rota A→B, dist, tempo, **geometria**) | **Backend** | OSRM (já temos) | ⚠️ falta expor a geometria |
| **3. Navegação turn-by-turn** (voz, "vire à direita") | SDK pago (Google/Mapbox) | — | ❌ fora de escopo |

**Conclusão:** o backend só precisa de **uma adição**: começar a devolver a **geometria
da rota** (a linha que o app desenha). Hoje o OSRM é chamado com `overview=false`
(`OsrmMapsService.java:68`) e o `RouteInfo` só tem `distanciaKm` + `tempoMin` —
nenhuma geometria sai do backend.

O movimento do pin do motorista **já funciona**: `driver_current_lat/lng` via polling
(regra 10) + `RideResponse.driverDistanceKm`. O SSE de status (regra 18) já cobre as
transições. Nada disso muda.

---

## 2. Decisão de design — geometria é estática, não pode entrar no polling

A geometria de uma rota **não muda** durante a corrida (origem/paradas/destino são
fixos). Logo:

- ❌ **NÃO** incluir o polyline no `RideResponse` — ele é devolvido a cada poll de 5s
  (`GET /rides/{id}`) e a regra 3 exige polling leve. Um polyline urbano tem alguns KB;
  multiplicar isso por um poll a cada 5s é desperdício puro.
- ✅ O front busca a geometria **uma única vez** (ao abrir/aceitar a corrida), guarda em
  memória e segue só com o polling leve para status + posição do motorista.

Por isso a geometria sai por **endpoints de uso único**, não pelo polling.

---

## 3. Mudanças no backend (passo a passo)

### Passo 1 — OSRM passa a trazer a geometria
`OsrmMapsService.fetchFromOsrm` (`OsrmMapsService.java:56`):
- Trocar `queryParam("overview", "false")` por `overview=full` + `geometries=polyline`.
- `polyline` (Encoded Polyline Algorithm, precisão 5) é compacto e tem decoder pronto
  no Flutter (`flutter_polyline_points`). Evitar `geojson` (verboso).
- Adicionar `String geometry` ao record `OsrmRoute` e ler `first.geometry()` no `parse`.

### Passo 2 — `RouteInfo` carrega a geometria
`RouteInfo.java`:
```java
public record RouteInfo(BigDecimal distanciaKm, int tempoMin, String geometry) {}
```
Ajustar os 2 construtores/usos no `OsrmMapsService` e os mocks em
`OsrmMapsServiceTest` / `RideServiceTest`.

### Passo 3 — `route_cache` guarda a geometria (migration V13)
A geometria é cara de buscar e nunca muda → cacheia junto (respeita a regra 11).
`V13__route_cache_geometry.sql`:
```sql
ALTER TABLE route_cache ADD COLUMN geometry TEXT;
```
- Nullable: linhas antigas do cache não têm geometria. No `route()`, se houver hit de
  cache **sem** geometria, tratar como miss parcial (rebuscar no OSRM e dar `UPDATE`),
  ou simplesmente aceitar null e o front cai num fallback (linha reta). Recomendo
  rebuscar+update — uma vez só por rota.
- Adicionar `geometry` em `RouteCache.java` + `setGeometry`/`getGeometry` no `persist`.

### Passo 4 — guardar a geometria na `Ride` (migration V13, mesma)
Para o front não depender do `route_cache` nem refazer hash:
```sql
ALTER TABLE rides ADD COLUMN route_geometry TEXT;
```
- `Ride.java`: campo `routeGeometry` (coluna `route_geometry`).
- `RideService.create` (`RideService.java:96-127`): ao calcular a rota, gravar
  `ride.setRouteGeometry(route.geometry())`.
- **LAZY-friendly:** é só uma coluna `TEXT` na própria `rides`; como NÃO entra nas
  projeções DTO do mural/histórico (regra 3), não pesa nos polls.

### Passo 5 — geometria no preview (`EstimateResponse`)
`POST /rides/estimate` é chamado **uma vez** antes de criar a corrida → ideal para
desenhar a rota na tela de confirmação ("igual Uber"). Adicionar `String geometry` ao
`EstimateResponse` e preencher com `route.geometry()` em `RideService` (`:101`).

### Passo 6 — endpoint leve para a geometria da corrida criada
`GET /rides/{id}/route` → `{ "geometry": "<polyline>" }`.
- Acesso: passageiro dono / motorista aceitante (`RideService.assertParticipant`, já
  usado pelo SSE — regra 18).
- Lê `ride.routeGeometry` direto (sem OSRM, sem cache lookup). Front chama **uma vez**
  ao abrir a corrida.
- Adicionar a rota na tabela de Roles do CLAUDE.md.

### Passo 7 (opcional) — geometria no compartilhamento público
`SharedRideResponse` (regra 14): incluir `geometry` para a página `/share/{token}`
desenhar a rota também. Mantém a regra de **não expor dados sensíveis** (geometria é só
a linha do trajeto). Em estado final segue 410 GONE.

---

## 4. Frontend (fora deste repo — guia para o app Flutter)

| Item | Pacote sugerido | Custo |
|------|-----------------|-------|
| Mapa + tiles | `flutter_map` (tiles OSM) | Grátis |
| Decodificar polyline | `flutter_polyline_points` | Grátis |
| Marcadores | nativo do `flutter_map` | Grátis |

Fluxo no app:
1. Tela de estimativa → desenha rota com `EstimateResponse.geometry`.
2. Corrida criada/aceita → 1 chamada a `GET /rides/{id}/route`, guarda o polyline.
3. Loop normal: polling `GET /rides/{id}` (status + `driverCurrentLat/Lng`) e/ou SSE
   (regra 18) movem o **pin do motorista**. A linha da rota não é rebuscada.

⚠️ **Política de tiles OSM:** o tile server público do OpenStreetMap tem
[usage policy](https://operations.osmfoundation.org/policies/tiles/) e não serve apps em
produção/escala. Para o MVP/testes serve; ao crescer, usar um provedor com free tier
generoso (MapTiler/Stadia/Protomaps) ou self-host de tiles — **decisão do front, não
afeta o backend**.

---

## 5. Resumo das alterações no backend

| Arquivo / artefato | Mudança |
|--------------------|---------|
| `OsrmMapsService.java` | `overview=full&geometries=polyline`; ler/persistir geometria |
| `RouteInfo.java` | + campo `geometry` |
| `RouteCache.java` | + campo `geometry` |
| `Ride.java` | + campo `routeGeometry` |
| `RideService.java` | gravar geometria no `create`; expor no estimate |
| `EstimateResponse.java` | + campo `geometry` |
| **Novo** `GET /rides/{id}/route` no `RideController` | devolve geometria leve |
| `SharedRideResponse` (opcional) | + campo `geometry` |
| **Migration** `V13__route_cache_geometry.sql` | `route_cache.geometry` + `rides.route_geometry` |
| `OsrmMapsServiceTest`, `RideServiceTest` | ajustar mocks de `RouteInfo` |
| `CLAUDE.md` | nova rota na tabela de Roles + nota na regra 11/2 |

**Custo total: R$ 0.** Nenhuma chave, nenhum SDK pago, nenhuma dependência nova no
backend (Flutter ganha 2 pacotes grátis).

---

## 6. Fora deste plano (deixar claro)

- Turn-by-turn / navegação por voz → fora do escopo do MVP.
- Recalcular rota em tempo real se o motorista desviar → não fazemos; a linha é o
  trajeto orçamentário, igual à filosofia do `route_cache` (regra 11).
- Mapa em tempo real curva-a-curva → continua via polling textual + pin (regra 4/10).
