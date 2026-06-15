# Análise UniMove — Melhorias e Features para o MVP

> Documento de recomendações. Avaliação do backend e prioridades para levar o MVP a um piloto real (Remanso/BA).

## Visão geral

O backend está **maduro** para a mecânica de corrida: monólito modular limpo (só interfaces cruzam fronteiras), transações disciplinadas (OSRM fora de transação, sem prender conexão do pool), lock otimista no aceite, SSE (status/mural/chat) com `afterCommit`, cache de geocode/rota, login lockout, auto-offline de motorista, expiração de corrida, ratings, earnings, saved places, share link, multi-paradas, pickup ETA, painel de métricas do admin e **surge pricing (preço dinâmico por demanda, opt-in por cidade)**.

As lacunas são de **levar ao piloto real**, não de mecânica de corrida.

---

## Lacunas priorizadas

### Bloqueadores para piloto real
1. **Pagamento real (Mercado Pago Pix + split).** Hoje `SimulatedPaymentService` gera payload fictício. Item nº1 do piloto. Já existe a interface `PaymentService` — implementar `MercadoPagoPaymentService` sem tocar `RideService`. Precisa: webhook de confirmação Pix, status de pagamento na `Ride`, idempotência, split motorista/plataforma.
2. **Push notifications.** Só SSE hoje; passageiro com app fechado não sabe do aceite. Avaliar FCM (Firebase). Fora do escopo MVP no CLAUDE.md, mas é o gap funcional mais sentido no piloto.

### Segurança / robustez
3. **Token de suspenso válido até 24h.** `requireActive` só é checado em `create`/`accept`. Opções: checar status no `JwtAuthenticationFilter` (custo: 1 query/req — usar cache curto), ou denylist de tokens. Decisão de trade-off latência × segurança.
4. **Rate limiting global** em `/auth/register` e `/maps/geocode` (bate no Photon externo). Hoje só o login tem lockout. Avaliar bucket4j ou filtro simples por IP.
5. **CORS** não configurado — trava o painel admin web.

### Observabilidade (produção)
6. Só `/actuator/health` exposto. Adicionar Micrometer/Prometheus, correlation-id (MDC filter), expandir actuator (`info`, `metrics`). Operar o piloto sem isso é às cegas.

### Cobertura de testes
7. Núcleo bem coberto (`RideService`, login/lockout, JWT, maps/geocode-cache, surge, métricas, perfil, schedulers de expiração/auto-offline — 127 testes). Faltam: chat e saved-places.

### Dívida de escala (não-bloqueador)
8. Hubs SSE em memória — não sobrevivem a múltiplas instâncias. OK para single-instance no Railway; registrar dívida (Redis pub/sub no futuro).

---

## Features candidatas
- Recibo de corrida pós-`COMPLETED` (PDF/JSON com itens da tarifa).
- Corridas agendadas ("agendar para depois").
- Cupom de desconto / indicação (growth — bom para a narrativa de investidores).
- Rating do passageiro visível no mural antes do aceite (campo já existe em `UserRatingService`).
- Botão SOS / compartilhar viagem em andamento (share link já existe como base).
- ~~Dashboard admin com métricas (corridas/dia, receita, motoristas ativos).~~ ✅ **Implementado** — `GET /admin/metrics` (ver `docs/admin-metrics.md`).
- ~~Surge pricing (preço dinâmico por demanda).~~ ✅ **Implementado** — `SurgePolicy`, opt-in por cidade (ver `docs/plano-surge-pricing.md`).

---

## Verificação (ao implementar um item escolhido)
- `mvn test` (JUnit 5 + Mockito, sem Docker/Postgres — 127 testes em ~segundos). Validação com banco real é manual via `docs/smoke-test.md` + `docs/api.http`.
- Para pagamento: testar webhook com payload simulado e checar idempotência.
- `mvn spring-boot:run` + chamada manual ao endpoint novo.
