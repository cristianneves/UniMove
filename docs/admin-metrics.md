# Painel Admin — Métricas

`GET /admin/metrics` — role **ADMIN**. Agrega o estado do negócio para o dashboard.

## Parâmetros

| Param | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `from` | `date` (ISO `YYYY-MM-DD`) | não | Início do período (inclusivo). Default: `to` − 29 dias. |
| `to`   | `date` (ISO `YYYY-MM-DD`) | não | Fim do período (inclusivo). Default: hoje (UTC). |

- O período filtra corridas por **`created_at`** (data de criação da corrida).
- `from` posterior a `to` → **400** (`InvalidMetricsRangeException`).
- `users` é a **fotografia atual** da base (não filtra por período — é estado corrente).

## Resposta `200`

```json
{
  "from": "2026-05-13",
  "to": "2026-06-11",
  "rides": {
    "total": 142,
    "completed": 118,
    "cancelled": 16,
    "expired": 5,
    "active": 3,
    "completionRate": 0.831,
    "cancellationRate": 0.1127
  },
  "revenue": {
    "gross": 2480.50,
    "cancellationFees": 33.00,
    "averageTicket": 21.02
  },
  "users": {
    "totalPassengers": 87,
    "totalDrivers": 12,
    "onlineDrivers": 4,
    "pendingDrivers": 2,
    "suspendedUsers": 1
  },
  "byDay": [
    { "day": "2026-06-10", "totalRides": 9, "completedRides": 8, "revenue": 172.40 },
    { "day": "2026-06-11", "totalRides": 6, "completedRides": 5, "revenue": 110.00 }
  ]
}
```

### Notas de cálculo
- `active` = `total − completed − cancelled − expired` (corridas em estado não-final no período).
- `completionRate` / `cancellationRate` = fração em `[0,1]` (4 casas), `0` quando não há corridas.
- `revenue.gross` = soma de `preco` das corridas **COMPLETED** no período.
- `averageTicket` = `gross / completed` (2 casas), `0` quando não há concluídas.
- `byDay`: série diária por `created_at` — `totalRides` criadas no dia, quantas concluíram e a receita correspondente. Dias sem corrida não aparecem (o front preenche o eixo).

## Arquitetura
- Controller: `AdminMetricsController` (`/admin/metrics`).
- Service: `AdminMetricsService` (domain.ride) — agrega corridas/receita e consome `UserStatsService` (domain.user) via DTO `UserStatsSnapshot`, respeitando a regra de fronteira (nenhuma entidade JPA cruza o pacote).
- Queries: `RideRepository.aggregateRideMetrics` (JPQL, 1 query) e `findRideMetricsByDay` (nativa, série diária).
