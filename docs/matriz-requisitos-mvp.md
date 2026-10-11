# Matriz de requisitos do MVP — evidência do backend

Card: **[QA][P0] Contratos Flutter–Spring e matriz dos 19 requisitos**. Requisitos conforme o guia do quadro (card "📘 GUIA KANBAN").

**Ambiente da execução:** Postgres 16.15 (container limpo, porta 55432), API em porta aleatória com Flyway V1–V35 e Hibernate `validate`. Branch `feature/mvp-p0-backend`, 10/10/2026. Comando: `IT_DATABASE_URL=jdbc:postgresql://localhost:55432/postgres ./mvnw verify`. Resultado: **473 testes unitários + 23 ITs, 0 falhas**.

**O que cada status significa:**
- **PASSOU (backend)**: contrato HTTP exercitado contra Postgres real, ou regra coberta por teste automatizado. Isso **não** prova o requisito no aparelho.
- **NÃO EXECUTADO**: depende do app, de aparelho físico ou de ambiente que este repositório não controla.
- **BLOQUEADO**: depende de decisão ou de acesso externo.
- **FORA DO PILOTO**: excluído por decisão registrada no Go/No-Go.

## Requisitos funcionais

| Req | Escopo | Backend | Evidência | App/aparelho |
|---|---|---|---|---|
| RF01 | cadastro/login | **PASSOU** | `AuthContractIT` (8): desafio WhatsApp → `verificationToken` → cadastro → login → `/users/me`; token de uso único; cidade fora de `/cities` não gasta o token; `PHONE_IN_USE`; desafio e token vencidos; ADMIN/termos/veículo → 400; e-mail repetido → 409. Também `AuthServiceRegisterTest`, `AuthServiceLoginLockoutTest`, `PhoneVerificationServiceTest`, `WhatsAppWebhookControllerTest`, `SocialAuthServiceTest` | NÃO EXECUTADO: card [RF01] (Flutter sem `verificationToken`/slug). WhatsApp real: BLOQUEADO (ver RNF06) |
| RF02 | motorista (documentos, aprovação) | **PASSOU** | `DriverDocumentIT` (envio concorrente → um `SUBMITTED`), `DriverDocumentServiceTest`, `DocumentFileInspectorTest`, `DriverServiceTest`; `AuthContractIT.driverRegistrationStartsUnapproved` | NÃO EXECUTADO: bucket Supabase real em homologação |
| RF03 | pedido de corrida | **PASSOU** | `RideJourneyIT` (estimativa, pedido, mural), `RideIT.doubleCreateIsBlocked` (409), `RideServiceTest` | NÃO EXECUTADO: card [RF03/RF04] coordenadas de demo é só do app. O backend aceita qualquer coordenada válida e não tem como distinguir uma de demonstração |
| RF04 | geolocalização | **PASSOU** | `RideJourneyIT`: GPS aceito, salto impossível → 422, não participante → 403, posição apagada ao concluir. `DriverLocationPolicyTest` (velha, futura, imprecisa, fora de ordem) | NÃO EXECUTADO: GPS em background no aparelho |
| RF05 | aceite/cancelamento | **PASSOU** | `RideIT.concurrentAcceptHasSingleWinner` e `RideJourneyIT` (200 + 409 na disputa; perdedor sem acesso); `RideJourneyIT.cancellationRules` (motorista sem motivo → 400; admin com motivo, SSE avisa; terminal → 409); passageiro em andamento → 409 | NÃO EXECUTADO: dois aparelhos |
| RF06 | preço | **PASSOU** | `SurgePolicyTest`, `RideServiceTest` (preço calculado no servidor), `RideJourneyIT` (estimativa > 0). Cupons: card V2, fora do MVP | — |
| RF07 | comunicação | **PASSOU** | `RideJourneyIT`: chat por SSE real; só participantes (perdedor e estranho → 403); só a caminho ou em andamento (antes do aceite e depois de concluir → 403). Push: card V2 | NÃO EXECUTADO: chat em aparelho |
| RF08 | histórico/avaliação | **PASSOU** | `RideJourneyIT.completedRide`: avaliação única (2ª → 409), avaliação bilateral, histórico do passageiro e do motorista, isolamento entre usuários. `RideIT.invalidSortIsBadRequest` | — |
| RF09 | pagamento | **PASSOU (só dinheiro)** | `RideJourneyIT.pilotIsCashOnly` (Pix → 409 com `PAYMENT_PIX_ENABLED=false`); `completedRide` (dinheiro `PENDING` → `CONFIRMED`, 2º registro → 409); `RideServiceTest` (divergente, troco, Pix `SIMULATED` quando ligado) | Pix real: FORA DO PILOTO (decisão de 10/10/2026) |
| RF10 | admin | **PASSOU** | `RideJourneyIT` (detalhe e cancelamento pelo admin; passageiro → 403), `AdminRideControllerWebMvcTest`, `AdminAuditServiceTest`, `AdminMetricsServiceTest`, `CityCatalogServiceTest`, `NotificationServiceTest`, `SessionAndDocsIT.swaggerRequiresAdminLogin` | Denúncias/suporte (card P1 sem tag MVP): fora deste recorte |

## Requisitos não funcionais

| Req | Escopo | Backend | Evidência | Pendência |
|---|---|---|---|---|
| RNF01 | usabilidade | NÃO EXECUTADO | mensagens de erro em PT-BR no `ApiError` (verificadas nos ITs: cidade, expiração, Pix) | teste com usuários: app |
| RNF02 | responsividade | NÃO EXECUTADO | — | app |
| RNF03 | desempenho | NÃO EXECUTADO | — | card RNF03 (P1, sem tag MVP): metas e medição |
| RNF04 | segurança | **PASSOU** | `AuthContractIT.authenticationAndRoles` (401/403 por papel), `SecurityWebMvcTest`, `JwtServiceTest`, `RateLimitFilterTest`, `ClientIpTest`, `SessionAndDocsIT` (revogação de token, Swagger só admin), `ObservabilityIT` (actuator fechado além de health/info), `FieldCryptoTest` | rotacionar a senha do admin do seed em prod |
| RNF05 | privacidade/LGPD | **PASSOU** | `AccountPrivacyServiceTest`, `RidePrivacyServiceTest`, `RideRetentionSchedulerTest`, `DocumentRetentionSchedulerTest`; telefone mascarado no desafio (`AuthContractIT`) | — |
| RNF06 | disponibilidade | **PASSOU (código)** / BLOQUEADO (infra) | `ObservabilityIT` (health, liveness, readiness com banco, info com commit); `DestructiveMigrationGuardTest` (trava da V19, 8 casos) e boot real bloqueado num banco em V18 com dados; `scripts/smoke.sh` validado localmente | health no Render, monitor externo, backup agendado e WhatsApp real: operador (runbook §8) |
| RNF07 | consumo de rede / rede instável | **PASSOU (parcial)** | snapshot do SSE ao reconectar (`RideJourneyIT` recebe o estado atual ao abrir o stream); `Last-Event-Id` no chat (`ChatService`); idempotência de aceite e de pedido (409) | backoff e modo avião: app |
| RNF08 | escala | **PASSOU (concorrência)** | aceite concorrente com um vencedor (`RideIT`, `RideJourneyIT`), índice único de corrida ativa (V23), envio de documento concorrente (`DriverDocumentIT`) | teste de carga: card RNF08 (P1). SSE em memória = instância única (documentado) |
| RNF09 | compatibilidade | NÃO EXECUTADO | — | matriz Android/iOS: app |

## Como reproduzir

```bash
docker run -d --rm --name unimove-it-pg -e POSTGRES_PASSWORD=it -p 55432:5432 postgres:16-alpine
IT_DATABASE_URL=jdbc:postgresql://localhost:55432/postgres ./mvnw verify
```

Sem `IT_DATABASE_URL` os ITs são pulados e o `verify` continua passando. No CI (`.github/workflows/deploy.yml`) um Postgres 16 de serviço roda os ITs em todo PR para a `main`.
