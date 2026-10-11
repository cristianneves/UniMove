# Go/No-Go do piloto — critérios e estado do backend

Card: **[RELEASE][P0] Critérios objetivos de piloto e Go/No-Go**. Piloto: Remanso/BA.

Este documento **não dá porcentagem de prontidão**. Cada critério tem dono, evidência e status. A decisão só é **Go** quando todos os critérios bloqueantes estiverem ✅ ou tiverem exceção aceita e assinada na seção 4.

Estado em 10/10/2026: **NO-GO**. O backend dos cards está entregue (seção 2). Falta uma pendência de backend, a primeira execução dos ITs no CI do GitHub (seção 2.1, B2) e os critérios de app, de aparelho e de infraestrutura (seção 3).

## 1. Decisões de escopo (por escrito)

| # | Decisão | Data | Consequência no backend |
|---|---|---|---|
| D1 | **Piloto só em dinheiro.** Pix real (PSP) vira V2 | 10/10/2026 | `PAYMENT_PIX_ENABLED=false` (padrão): `confirm-payment` com Pix → 409. Pix simulado nunca aparece como recebimento (`SIMULATED`) |
| D2 | **Sem push e sem processamento em segundo plano.** Comunicação = chat in-app + SSE com o app aberto | herdada do escopo MVP (CLAUDE.md) | o motorista precisa estar com o app aberto no mural. Mitigação: `snapshot` do SSE ao reabrir e auto-offline do motorista inativo |
| D3 | **Matching = mural por cidade + categoria**, sem raio de proximidade | herdada do escopo MVP | qualquer motorista online e aprovado da cidade vê a corrida. Adequado a uma cidade pequena |
| D4 | **V19 só roda com liberação explícita** | 10/10/2026 | boot abortado em banco abaixo da V19 com usuários reais (`UNIMOVE_ALLOW_DESTRUCTIVE_V19`) |

## 2. Critérios de backend: ✅ cumpridos

| Critério | Evidência |
|---|---|
| Cadastro com telefone verificado e cidade por slug | `AuthContractIT` (8 cenários) contra Postgres real |
| Fluxo da corrida completo, um único aceite, chat restrito, avaliação única, histórico | `RideJourneyIT` (4) + `RideIT` (4), SSE e chat abertos de verdade |
| Pagamento em dinheiro com registro operacional e conciliação | `RideJourneyIT.completedRide` / `.pilotIsCashOnly`; `/admin/rides?paymentStatus=DIVERGENT` |
| Permissões por papel, revogação, rate limit, segredos fora do código | `AuthContractIT.authenticationAndRoles`, `SecurityWebMvcTest`, `SessionAndDocsIT`, `RateLimitFilterTest` |
| Aprovação documental do motorista | `DriverDocumentIT`, `DriverDocumentServiceTest`; `/admin/drivers/{id}/approve` exige documentação aprovada |
| Health, readiness com banco, versão e commit publicados | `ObservabilityIT`; `/actuator/health/readiness`, `/actuator/info` |
| V19 analisada e travada; backup e rollback documentados | `DestructiveMigrationGuardTest` + boot real bloqueado; `docs/homologacao-runbook.md` §1 e §3 |
| Smoke reproduzível | `scripts/smoke.sh`, validado localmente (11/11) |
| Testes de contrato barram o merge | CI com Postgres 16 de serviço configurado para rodar os 23 ITs em todo PR (primeira execução no GitHub pendente: B2) |
| Matriz de requisitos | `docs/matriz-requisitos-mvp.md` |

## 2.1 Pendências de backend: ⏳ abertas

| # | Pendência | Dono |
|---|---|---|
| ~~B1~~ ✅ | `PUT /users/me/preferences` com `preferredPaymentMethod=PIX` e o Pix desligado → 400 (11/10/2026, `AccountPrivacyServiceTest`) | backend |
| B2 | Primeira execução dos ITs no CI do GitHub (só roda com o PR aberto) | backend |
| ~~B3~~ ✅ | PR `feature/mvp-p0-backend` → `main` mergeado (PR #26) | backend |

## 3. Critérios bloqueantes: ⏳ pendentes (fora deste repositório)

| # | Critério | Dono | Card |
|---|---|---|---|
| P1 | App envia `verificationToken` e `cidade` (slug de `/cities`) no cadastro; sem credencial demo no build de release | app | [RF01][P0] |
| P2 | App não usa coordenadas de demonstração como origem | app | [RF03/RF04][P0] |
| P3 | App esconde o Pix (o backend já recusa com 409) | app | [RF01]/[RF09] |
| P4 | `PHONE_VERIFICATION_CHANNEL=WHATSAPP` em homologação com número real da Meta, e um cadastro feito do celular | operador | [RNF06][P0] |
| P5 | `scripts/smoke.sh` contra o Render: health em várias tentativas, saída anexada ao card | operador | [RNF06][P0] |
| P6 | Monitor externo em `/actuator/health/readiness` com alerta | operador | [RNF06][P0] |
| P7 | Backup do banco feito **e restaurado** num banco descartável (prova de restauração) | operador | [RNF06][P0] |
| P8 | `DATA_ENCRYPTION_KEY`, `JWT_SECRET` e bucket `driver-documents` configurados em prod; chave de cifra guardada fora do Render | operador | [RNF06][P0] |
| P9 | Senha do admin do seed trocada em prod (o boot loga `ERROR SEGURANCA` enquanto não trocar) | operador | [RNF04] |
| P10 | E2E em dois celulares + admin: cadastro, pedido, disputa de aceite, GPS, chat, conclusão, dinheiro, avaliação, com rede instável (3G, modo avião, app em segundo plano) | QA | [INTEGRAÇÃO][P0], [PASSAGEIRO][P0] |
| P12 | Abrir a cidade do piloto (`POST /admin/cities` com `Remanso`) e desativar `sao-jose-do-rio-preto`, que vem do seed do admin, para não aparecer no seletor do cadastro | operador | [RF01] |
| P11 | Motoristas do piloto com documentação aprovada **depois** do deploy da V27 (antes disso, motorista aprovado perde o acesso ao online) | operador | runbook §2 |

## 4. Riscos aceitos (assinar antes do Go)

| Risco | Mitigação | Aceito por / data |
|---|---|---|
| OSRM e Photon públicos, sem SLA | cache de rota e geocode no banco; falha vira 503 explícito | |
| Render free hiberna (primeira requisição demora) | smoke e monitor com retentativa; plano pago se o piloto crescer | |
| SSE em memória: uma única instância | documentado; broker só quando houver escala (card RNF08) | |
| Sem push: o motorista perde corridas com o app fechado | mural por SSE + auto-offline; push fica no card V2 | |
| GPS em segundo plano sem homologação em aparelho | o backend recusa posição velha, imprecisa ou com salto impossível | |

## 5. Rollback

1. **Código:** reapontar o Render para a imagem anterior pelo SHA (`docs/deploy.md`). As migrations até a V35 são aditivas, então a versão anterior sobe sobre o banco atual.
2. **Banco:** só por restauração do backup (P7). O Flyway não desfaz migration.
3. **Pausar novos cadastros:** `DELETE /admin/cities/{slug}` tira a cidade do seletor sem afetar quem já usa (desativação soft).

## 6. Assinatura

| Papel | Nome | Decisão | Data |
|---|---|---|---|
| Produto | | | |
| Backend | | | |
| App | | | |
| Operação | | | |
