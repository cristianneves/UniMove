# Contrato para o app — o que mudou nesta versão

Branch `feature/mvp-backend-gaps`. Esta lista é o que o Flutter precisa conhecer. Detalhes de cada rota estão no Swagger (`/swagger-ui.html`).

## ⚠️ Mudanças que quebram o app atual

1. **Cadastro exige `termsVersion`.** `POST /auth/register` e `POST /auth/social/register` recebem `"termsVersion": "<versão>"`, obtida em `GET /legal/terms` (público). Versão diferente da vigente → 400. O app precisa mostrar o link `policyUrl` e o aceite na tela de cadastro.
2. **Motorista só opera com documentação.** Aprovar exige documentação aprovada, e ficar online ou aceitar corrida exige CNH válida. Motorista aprovado antes desta versão recebe 403 `"CNH vencida ou documentação incompleta..."` até completar o fluxo do item 4.
3. **Conta suspensa ou excluída cai na hora.** Qualquer rota protegida passa a responder 403 (suspensa, com a mensagem de suporte) ou 401 (excluída), mesmo com JWT ainda válido. O app deve tratar o 403 de suspensão mostrando a mensagem, e o 401 voltando para o login.

## 1. Segurança

- `429` com header `Retry-After` em login, cadastro, social e `/maps/*` quando o limite estoura.

## 2. Corrida

- Corrida ativa duplicada (clique duplo) agora é sempre 409, mesmo em requisições simultâneas.
- `PUT /rides/{id}/driver-location` aceita `recordedAt` (ISO-8601, horário do GPS) e `accuracyM` (metros), ambos opcionais mas **recomendados**. Respostas:
  - **422** com mensagem quando a posição está velha (>120 s), no futuro (>30 s), imprecisa (>100 m) ou com salto impossível (>180 km/h). O app deve mostrar a mensagem e esperar o próximo fix.
  - **200** sem alterar a posição quando ela chega fora de ordem.
- Depois de `complete` ou `cancel`, a posição do motorista volta `null`.

## 3. Pagamento em dinheiro

- `POST /rides/{id}/confirm-payment`: `{"method":"DINHEIRO","changeFor":50.00}`. `changeFor` (troco para quanto) é opcional e precisa ser maior ou igual ao preço; com `PIX` → 400.
- Novos campos em `RideResponse`: `paymentStatus` (`PENDING` | `CONFIRMED` | `DIVERGENT` | `SIMULATED`), `cashChangeFor`, `cashReceivedAmount`, `paymentConfirmedAt`, `paymentNote`. No mural: `cashChangeFor`. No histórico: `paymentStatus`.
- **Motorista, depois de concluir:** `POST /rides/{id}/cash-payment` com `{"receivedAmount": 20.00, "note": "..."}`. Valor abaixo do preço exige `note` e vira `DIVERGENT`. É registro único (segundo envio → 409).
- Pix continua **simulado** (`SIMULATED`). O app não deve exibir "pago".

## 4. Documentação do motorista

| Rota | Uso |
|---|---|
| `GET /drivers/me/documentation` | `status` (`INCOMPLETE`/`REJECTED`/`PENDING_REVIEW`/`EXPIRED`/`APPROVED`), `profile`, `current` (envio vigente por tipo), `history` e `pending` (textos prontos para a tela, incluindo o motivo das reprovações) |
| `PUT /drivers/me/documentation/profile` | `cpf`, `cnhNumber` (11 dígitos), `cnhCategory` (A, B, C, D, E, AB, AC, AD, AE), `cnhExpiresOn` (data), `vehicleBrand`, `vehicleModel`, `vehicleYear`, `vehicleColor`, `vehiclePlate`. Cadastro aprovado → 409, exceto com a CNH vencida |
| `POST /drivers/me/documentation/documents/{CNH\|CRLV\|SELFIE}` | multipart, campo `file`. JPG/PNG (e PDF para CNH/CRLV), até 5 MB. Só aceita o primeiro envio ou o reenvio de documento reprovado. 503 se o storage estiver fora |

Admin: `GET /admin/drivers/{id}/documentation`, `GET .../documents/{docId}/file` (URL assinada de 5 min), `POST .../documents/{docId}/approve`, `POST .../documents/{docId}/reject {"reason":"..."}` e `GET /admin/audit`.

## 5. Privacidade

| Rota | Uso |
|---|---|
| `GET /legal/terms` | `{version, policyUrl}`, público |
| `POST /users/me/terms {"version":"..."}` | reaceite quando `termsUpToDate=false` |
| `GET/PUT /users/me/preferences` | `{"marketingOptIn": true\|false}`; `transactionalNotifications` é sempre `true` |
| `GET /users/me/data-export` | JSON com os dados do usuário |
| `DELETE /users/me` | corpo `{"password":"..."}` (vazio para conta Google). 204; 409 com corrida ativa |

## 6. Erros que deixaram de ser 500

- Enum ou UUID inválido no path ou na query (ex.: `?status=FOO`) → **400**.
- Parte multipart ausente → **400**. Arquivo acima do teto → **413**.
