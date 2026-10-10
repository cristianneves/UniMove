# Plano — cards MVP pendentes (recorte backend)

Origem: quadro Trello "UniMove — Desenvolvimento do App", lista **Preparada**, tag verde **MVP** (10/10/2026).
Branch única: `feature/mvp-p0-backend`. Trabalho de app/aparelho real fica fora deste plano.

| Card | Recorte backend |
|---|---|
| [RNF06][P0] Homologação da API, banco, V19 e observabilidade | trava da V19, actuator, smoke script |
| [RF01][P0] Corrigir cadastro real: WhatsApp, token e cidades | IT de contrato do cadastro |
| [QA][P0] Contratos Flutter–Spring e matriz dos 19 requisitos | ITs de contrato + matriz RF/RNF |
| [INTEGRAÇÃO][P0] E2E real: passageiro × motorista × admin | IT de jornada completa pela API |
| [RF03/RF04][P0] Remover coordenadas de demonstração | nada no backend (só app) — registrado na matriz |
| [PASSAGEIRO][P0] Validar corrida realtime, chat e avaliação | IT de SSE, chat e avaliação |
| [RELEASE][P0] Critérios objetivos de piloto e Go/No-Go | `docs/go-no-go-piloto.md` |

Decisões do dono do produto (10/10/2026): trava no boot para a V19; piloto **só dinheiro** (Pix real vira V2).

## 1. RNF06 — homologação

- [x] Callback `beforeMigrate` do Flyway: aborta o boot se o banco está abaixo da V19 com usuário não-ADMIN; libera só com `UNIMOVE_ALLOW_DESTRUCTIVE_V19=true`
- [x] Actuator: `health` com probes e componente do banco, `info` com versão e commit
- [x] `scripts/smoke.sh <url>`: health com retentativa, `/cities`, `/legal/terms`, login de conta de smoke, `/users/me`
- [x] Runbook atualizado

## 2. Contratos (QA, RF01, E2E, PASSAGEIRO)

- [x] `AuthContractIT`: desafio → cadastro com `verificationToken`; token reusado/inexistente, telefone já usado, `ADMIN`, cidade inválida, login, `/users/me`, 401/403
- [x] `RideJourneyIT`: estimate → criar → mural → aceite → GPS → SSE → start/complete → avaliação única → histórico; cancelamento; admin
- [x] Chat: só participante, limites de estado (dentro do `RideJourneyIT`)
- [x] Pagamento: `SIMULATED` ≠ `CASH` declarado
- [ ] `docs/matriz-requisitos-mvp.md`

## 3. RELEASE

- [ ] `docs/go-no-go-piloto.md`

## 4. Fechamento

- [ ] `mvn verify` + ITs contra Postgres real
- [ ] README atualizado, push da branch
- [ ] Lista de cards para mover no Trello (token é só leitura)
