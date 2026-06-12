# Plano — Surge Pricing (preço dinâmico por demanda)

> ✅ **IMPLEMENTADO** na branch `feature/surge-pricing` (127 testes verdes).
> Spec consolidado após sessão de design (grill-me). Objetivo: aproximar o UniMove do
> comportamento Uber com **preço dinâmico automático**, demonstrável numa demo de investidor.
> Escopo: **apenas backend**. Migrations aplicadas: **V17** (surge config) e **V18** (surge_multiplier)
> — V15/V16 já estavam ocupadas (pickup_eta, addresses).

## Decisões travadas

| Tema | Decisão |
|---|---|
| Gatilho | **Automático** — sinal de oferta × demanda ao vivo, por `(cidade, category)` |
| Sinal | `demanda ÷ oferta` |
| Demanda | nº de rides em `AVAILABLE_IN_MURAL` na cidade+categoria (já indexado) |
| Oferta | **motoristas disponíveis** = `online = true` **e** sem corrida ativa (`DRIVER_EN_ROUTE`/`IN_PROGRESS`) na cidade+categoria |
| Curva | **Degraus (tiers)** — patamares discretos, suavização natural |
| Teto | **1.5x** (defensável p/ cidade pequena; configurável por cidade) |
| Trava de preço | **Já existe** — `RideService.create` faz `ride.setPreco(preco)`; surge entra no cálculo do `create` e congela junto |
| Auditoria | gravar multiplicador aplicado em coluna nova da `Ride` |
| Transparência | expor multiplicador no `EstimateResponse` (app mostra "1.3x") |

### Ladder default (configurável)
| ratio (demanda/oferta) | multiplicador |
|---|---|
| < 1.0 | 1.0x |
| 1.0 – 1.5 | 1.2x |
| 1.5 – 2.0 | 1.35x |
| ≥ 2.0 (ou oferta = 0) | 1.5x (teto) |

## Arquitetura (segue o padrão da `PricingPolicy`)

- **`SurgePolicy`** (novo `@Component`, irmão de `PricingPolicy`): expõe
  `BigDecimal multiplier(String cidade, RideCategory category)`.
  - Consulta contagens de demanda e oferta (repos abaixo), aplica a ladder, respeita teto.
  - Kill switch + teto por cidade carregados em cache `volatile` recarregado no upsert do admin
    (mesmo mecanismo de `PricingPolicy.reload()`).
- **Cálculo único compartilhado** por `estimate()` e `create()` — garante que o preview e o
  preço travado leem o mesmo estado (evita ver 1.2x e ser cobrado 1.35x).
- Cálculo roda **fora de transação** (como o `estimate` hoje), sem prender conexão do pool.

### Pontos de integração
- `PricingPolicy.calculate(...)` permanece intocada (preço base). O surge multiplica o
  resultado: `precoFinal = base.multiply(mult).setScale(2, HALF_UP)`.
- `RideService.estimate`: aplicar `mult` em cada `CategoryOption` e no `preco`; adicionar `mult`
  ao `EstimateResponse`.
- `RideService.create`: aplicar `mult` antes de `ride.setPreco(...)`; persistir `surge_multiplier`.

## Banco de dados

- **`V17__surge_config.sql`** — controle por cidade+categoria:
  colunas `surge_enabled BOOLEAN`, `surge_cap NUMERIC(3,2)` em `pricing_configs`
  (reusa a tabela existente; default `surge_enabled = false`, `surge_cap = 1.50`).
- **`V18__ride_surge_multiplier.sql`** — `rides.surge_multiplier NUMERIC(3,2) NOT NULL DEFAULT 1.00`
  (auditoria + recibo + `/admin/rides`).
- Ladder de patamares: hardcoded no `SurgePolicy` no MVP (ratio→mult é regra de produto estável);
  só `enabled` e `cap` são configuráveis pelo admin. Revisar se o piloto pedir ladder por cidade.

## Repositórios / queries
- Demanda: `countByStatusAndCidadeAndCategory(AVAILABLE_IN_MURAL, cidade, category)` (RideRepository).
- Oferta: contagem de `drivers.online = true` por cidade+categoria **menos** motoristas com ride
  ativa — subquery ou duas contagens. Sem corrida ativa = disponível.

## Admin
- `PUT /admin/pricing` passa a aceitar `surgeEnabled` e `surgeCap` (upsert); `reload()` recarrega
  `PricingPolicy` **e** `SurgePolicy`.
- `GET /admin/rides` e `/admin/metrics` ganham visibilidade do `surge_multiplier` (receita com surge).

## Testes (JUnit 5 + Mockito, sem Docker)
- `SurgePolicyTest`: ladder (cada faixa), teto, oferta = 0 → teto, `enabled = false` → 1.0x.
- `RideServiceTest`: estimate e create usam o **mesmo** multiplicador; `surge_multiplier` persistido;
  preço travado = base × mult.
- Casos negativos: cidade sem config → 1.0x; categoria sem demanda → 1.0x.

## Fora de escopo (anotado)
- Suavização temporal/hysteresis (degraus já suavizam o suficiente p/ MVP).
- Surge agendado por horário; ladder configurável por cidade; notificar passageiro de queda de surge.

## Verificação
- `mvn test` (no CI — Testcontainers quebra na máquina de dev por bug do Docker Desktop).
- Smoke manual: criar demanda > oferta na mesma cidade/categoria, conferir `EstimateResponse.mult`
  e `rides.surge_multiplier` gravado.
