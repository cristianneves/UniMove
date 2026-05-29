# Plano — Busca de endereço + pin no mapa (geocoding) estilo Uber/99

> **Objetivo:** permitir que o passageiro defina origem/destino/paradas das duas formas
> que Uber e 99 oferecem:
> 1. **Digitando** o endereço e o app sugerindo resultados (autocomplete) → *forward geocoding*.
> 2. **Arrastando o pin** no mapa e o app mostrando o nome da rua daquele ponto → *reverse geocoding*.
>
> Ambos resolvem o mesmo problema: o backend hoje só aceita `lat/lng` (regra 2,
> `StopPoint`/`CreateRideRequest`). Falta a ponte **texto ↔ coordenada**.

---

## 1. Decisão: Photon (não Nominatim)

Os dois são gratuitos, open-source e baseados em OpenStreetMap (coerente com a stack
OSRM já usada). A escolha:

| Critério | **Photon** ✅ | Nominatim |
|----------|--------------|-----------|
| Autocomplete "as you type" | **Feito pra isso** (type-ahead, input parcial) | Não foi desenhado; a política da instância pública **proíbe** uso de autocomplete |
| Reverse geocoding (pin → rua) | Sim (`/reverse`) | Sim (`/reverse`) |
| Viés por localização | `lat`/`lon` (enviesa pro ponto) | `viewbox`+`bounded` |
| Instância pública grátis | `photon.komoot.io` (sem chave, fair-use) | `nominatim.openstreetmap.org` (**1 req/s**, sem autocomplete) |
| Self-host (quando escalar) | Docker, índice OSM | Docker, mais pesado |

**Veredito:** o requisito-cabeça é **autocomplete enquanto digita** — exatamente o que o
Nominatim público não permite e não faz bem. O Photon resolve forward **e** reverse com
um único provedor, sem chave, no mesmo espírito "grátis/OSM/self-hostável depois" do
OSRM. Por isso: **Photon**.

> Mesma ressalva do OSRM (regra 11): a instância pública não tem SLA. Se virar gargalo,
> próximo passo é self-host via Docker — uma linha no compose, sem mudar código.

---

## 2. Arquitetura — espelha o gateway do OSRM

Geocoding entra no domínio `maps` como um **segundo gateway**, irmão do OSRM, sem se
misturar com ele (base URL e responsabilidade diferentes):

```
domain.maps
├── MapsService / OsrmMapsService        (já existe — rotas)
├── GeocodingService / PhotonGeocodingService   (NOVO — texto ↔ coordenada)
├── GeoPlace                              (NOVO — resultado: label + lat/lng + campos)
├── GeocodeCache / GeocodeCacheRepository (NOVO — cache do reverse)
├── PhotonProperties + bean photonWebClient no MapsConfig
└── GeocodingController  →  /maps/geocode , /maps/reverse   (NOVO)
```

A interface:
```java
public interface GeocodingService {
    List<GeoPlace> search(String query, Double biasLat, Double biasLng, int limit); // forward
    GeoPlace reverse(double lat, double lng);                                       // reverse (pin)
}
```

`GeoPlace` (record): `displayName`, `lat`, `lng`, `street`, `city`, `state`.
É o DTO de saída direto da API (record serializa em JSON limpo).

---

## 3. Endpoints (ambos autenticados — `PASSAGEIRO`/`MOTORISTA`)

### `GET /maps/geocode` — digitar e achar (autocomplete)
```
GET /maps/geocode?q=avenida brasil 1200&limit=5&lat=-20.8197&lng=-49.3794
```
- `q` (**obrigatório**, mín. 3 chars), `limit` (default 5, máx. 10).
- `lat`/`lng` **opcionais**: o app passa o centro do mapa / GPS pra **enviesar** os
  resultados pra perto do usuário (igual Uber). É o substituto pragmático do "escopo por
  cidade" — não precisamos de centróide de cidade no banco; o app já sabe onde está.
- Resposta: `List<GeoPlace>` (lista de sugestões pro dropdown).

### `GET /maps/reverse` — arrastar o pin e descobrir a rua
```
GET /maps/reverse?lat=-20.8197&lng=-49.3794
```
- Resposta: um `GeoPlace` (o endereço mais próximo do ponto).
- **Cacheado** (ver §4).

Erros do Photon → `MapsUnavailableException` (HTTP 503), já tratado pelo
`GlobalExceptionHandler` (reuso, sem código novo).

---

## 4. Cache — só no reverse (igual `route_cache`, regra 11)

- **Reverse é cacheável:** arrastar o pin gera muitas coordenadas próximas; arredondando
  a **4 casas (~11m)** o reuso é alto. Tabela `geocode_cache`
  (`id, coord_hash TEXT UNIQUE, display_name, lat, lng, created_at`), chave =
  `round4(lat),round4(lng)`. Hit → retorna; miss → Photon, persiste, retorna. **Sem TTL**
  no MVP (mesma política do `route_cache`; `TRUNCATE` se precisar).
- **Forward (autocomplete) NÃO é cacheado:** queries parciais ("av", "ave", "aveni"...)
  têm baixíssima taxa de hit e o Photon é rápido. Em vez de cache, o **app deve fazer
  debounce de ~300ms** (só dispara a busca quando o usuário para de digitar) — isso já
  protege o fair-use do Photon. Documentado no guia do app.

Migration: **`V14__geocode_cache.sql`**.

---

## 5. Config

| Variável | Descrição | Default |
|----------|-----------|---------|
| `PHOTON_BASE_URL` | URL base do Photon | `https://photon.komoot.io` |

Property `app.photon.base-url` (espelha `app.osrm.base-url`). Bean `photonWebClient`
no `MapsConfig` com os mesmos timeouts do OSRM.

---

## 6. Arquivos (resumo)

| Arquivo | Mudança |
|---------|---------|
| `GeocodingService.java` | **novo** — interface (search + reverse) |
| `PhotonGeocodingService.java` | **novo** — impl WebClient + cache do reverse + parse GeoJSON |
| `GeoPlace.java` | **novo** — DTO de saída (label + lat/lng + campos) |
| `GeocodeCache.java` / `GeocodeCacheRepository.java` | **novo** — cache do reverse |
| `PhotonProperties.java` | **novo** — `app.photon.base-url` |
| `MapsConfig.java` | + bean `photonWebClient`; registra `PhotonProperties` |
| `GeocodingController.java` | **novo** — `GET /maps/geocode`, `GET /maps/reverse` |
| `V14__geocode_cache.sql` | **nova migration** |
| `PhotonGeocodingServiceTest.java` | **novo** — forward/reverse parse + cache hit/miss |
| `application.yml` | + `app.photon.base-url` |
| `CLAUDE.md` | regra 20 + rotas + env var |
| guia do app (`plano-mapa-mvp.md`) | seção de busca/reverse + debounce |

**Custo: R$ 0.** Sem chave, sem SDK pago. Photon público no MVP; self-host quando escalar.

---

## 7. Fluxo no app (Uber-like)

- **Campo de busca:** usuário digita → debounce 300ms → `GET /maps/geocode?q=...&lat&lng`
  (bias = mapa/GPS) → dropdown de sugestões → ao escolher, pega `lat/lng` do `GeoPlace`
  e usa como origem/destino/parada.
- **Pin no mapa:** usuário arrasta o pin → ao soltar, `GET /maps/reverse?lat&lng` →
  mostra `displayName` ("Av. Brasil, 1200 — Centro") como confirmação do ponto.
- O resultado (lat/lng) alimenta os mesmos `latOrigem/.../stops` que o
  `POST /rides/estimate` e `POST /rides` já consomem. **Nada muda no fluxo de corrida.**

---

## 8. Fora de escopo

- Centróide/bounding-box de cidade no banco (bias é via ponto enviado pelo app).
- Cache de autocomplete (resolvido com debounce no app).
- Self-host do Photon (só quando o público virar gargalo — igual OSRM).
