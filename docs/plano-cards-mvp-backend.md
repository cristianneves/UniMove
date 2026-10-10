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
- [x] `docs/matriz-requisitos-mvp.md` (+ ITs rodando no CI com Postgres de serviço)

## 3. RELEASE

- [x] `docs/go-no-go-piloto.md` (+ Pix desligado por padrão: `PAYMENT_PIX_ENABLED`)

## 4. Fechamento

- [x] `mvn verify` + ITs contra Postgres real (473 + 23, 0 falhas)
- [x] README atualizado, push da branch
- [x] Lista de cards para mover no Trello (token é só leitura)

## 5. Situação dos cards no Trello (mover/comentar à mão)

A parte de backend está entregue em todos os 7. Nenhum vai para **Concluídas**, porque cada um ainda tem parte de app, de aparelho ou de operação (regra do guia: não concluir por auditoria estática).

| Card | Sugestão | Comentário a colar |
|---|---|---|
| [RNF06][P0] Homologação | Em Andamento | backend ok (trava V19, probes, `scripts/smoke.sh`); faltam P4–P8 do `docs/go-no-go-piloto.md` |
| [RF01][P0] Cadastro real | Preparada (app) | contrato provado por `AuthContractIT`; falta o Flutter |
| [QA][P0] Contratos e matriz | Em Andamento | `docs/matriz-requisitos-mvp.md`; ITs rodando no CI; faltam os RNF de app |
| [INTEGRAÇÃO][P0] E2E real | Preparada (QA) | jornada via API em `RideJourneyIT`; falta rodar em dois aparelhos |
| [RF03/RF04][P0] Coordenadas demo | Preparada (app) | nada no backend |
| [PASSAGEIRO][P0] Realtime/chat | Preparada (QA) | SSE/chat/avaliação provados por `RideJourneyIT`; falta aparelho e rede instável |
| [RELEASE][P0] Go/No-Go | Em Andamento | `docs/go-no-go-piloto.md`: NO-GO até P1–P12 e assinaturas |
