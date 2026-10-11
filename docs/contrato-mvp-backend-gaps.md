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

---

## 7. Segundo ciclo (auditoria dos cards concluídos)

### ⚠️ Mais mudanças que quebram o app

4. **`PUT /users/me/password` devolve 200 com `AuthResponse`** (era 204 sem corpo). A troca **revoga todos os tokens anteriores**: o app precisa substituir o JWT pelo `token` da resposta e reabrir os streams SSE.
5. **`AuthResponse` ganha `mustChangePassword`.** Com `true` (senha temporária do reset), a API só aceita `PUT /users/me/password` e `GET /users/me`; o resto volta 403 *"Troque a senha temporária..."*. O app deve abrir a tela de troca.
6. **Novo 401 `"Sessão encerrada por alteração na conta (senha ou cidade). Entre novamente."`**: aparece em token emitido antes de uma troca de senha, reset ou troca de cidade. O app deve voltar ao login.
7. **`GET /admin/drivers/pending` virou paginado** (`Page`: `content`, `totalElements`...) e cada item traz `documentationStatus`.

### Corrida
- `POST /rides` aceita `expectedPrice` (o `preco` mostrado na estimativa). Se o valor recalculado for diferente → **409** com o preço novo na mensagem; o app mostra e pede confirmação.
- **Motorista pode cancelar em `IN_PROGRESS`**, sempre com `reason` e sem taxa. Passageiro em `IN_PROGRESS` recebe 409 explicativo.
- `cancelledBy` pode vir `ADMIN`.
- Histórico (`/rides/history`) traz `origemEndereco` e `destinoEndereco`.
- `/drivers/me/earnings` ganha `payments`: `cashReceived`, `cashPending`, `cashDivergentRides`, `pixSimulated`.

### Perfil
- `PUT /users/me/phone {"verificationToken": "..."}`: troca de número. Faça antes o desafio do WhatsApp com o número novo. 409 se o número é de outra conta.

### Notificações (in-app, sem push)
| Rota | Uso |
|---|---|
| `GET /notifications` | caixa de entrada paginada (`kind`: `PROMOTIONAL`/`TRANSACTIONAL`) |
| `GET /notifications/unread-count` | `{"unread": n}` para o badge |
| `POST /notifications/{id}/read` | marca como lida (204, idempotente) |

### Admin
| Rota | Uso |
|---|---|
| `GET /admin/users?q=&role=&status=&cidade=` | busca de usuários |
| `GET /admin/drivers/documents/pending` | fila de documentos aguardando análise |
| `GET /admin/rides?status=&cidade=&from=&to=&paymentStatus=` | filtros (datas ISO `yyyy-MM-dd`) |
| `GET /admin/rides/{id}` | detalhe da corrida |
| `POST /admin/rides/{id}/cancel {"reason":"..."}` | cancela corrida não terminal (auditado) |
| `GET /admin/campaigns/audience?cidade=&role=` | prévia: quantos receberiam |
| `POST /admin/campaigns {"title","body","cidade"?,"role"?}` | envia campanha (só para quem tem opt-in) |
| `GET /admin/campaigns` | histórico |

### Documentos do motorista
- `profile.cnhCheckDigitValid` (admin e motorista): sinal do dígito verificador da CNH. Não bloqueia.
- **Upload:** a foto é regravada no servidor, então o EXIF e a localização GPS da foto não são guardados. Imagem corrompida → 400; PDF com script ou anexo → 400.

---

## 8. Terceiro ciclo (reauditoria de 10/10/2026)

### ⚠️ Mudanças que o app precisa tratar

8. **Trocar de cidade revoga as outras sessões.** `PUT /users/me` com cidade nova devolve `token`. Esse token vale; os anteriores, inclusive em outros aparelhos, passam a responder 401 (mensagem do item 6). Os streams SSE abertos com eles são encerrados.
9. **Placa validada no cadastro** (`/auth/register` e `/auth/social/register`): só `ABC1234` ou `ABC1D23` (hífen opcional). Fora disso → 400 em `fieldErrors.vehiclePlate`. A placa é guardada sem hífen e em maiúsculas.
10. **Envio duplicado de documento** (clique duplo) → **409** *"Este documento já foi enviado e aguarda análise."*. Antes, o segundo envio ficava preso na fila do admin.

### Corrida
- **`GET /rides/{id}/share-link`** (passageiro dono) → `{"token":"…","path":"/share/{token}"}`. O app monta a URL com o host da API e compartilha. Com a corrida encerrada → 410; se não for o dono → 403. Antes não havia como obter o token, então o `/share/{token}` era inalcançável.
- `?sort=` com campo inexistente nas listas paginadas → **400** *"Parâmetro de ordenação inválido."* (era 500).

### Preferências
- `GET/PUT /users/me/preferences` ganha `preferredPaymentMethod` (`PIX`/`DINHEIRO`) e `preferredCategory` (`MOTO`/`CARRO`). O app usa os dois para pré-selecionar o pedido.
- O PUT **substitui** as preferências: se o campo vier ausente, a preferência é apagada. Por isso o app deve reenviar os valores atuais junto com o `marketingOptIn`.
- Esses dois campos são só do passageiro: motorista que os envia recebe 400.

### Estados do documento — equivalência com o card RF02/RF10
O backend usa 3 estados por envio e um consolidado derivado. Não existe `UNDER_REVIEW` separado porque só um papel (ADMIN) analisa.

| Card | Backend (por envio, `DriverDocumentStatus`) | Consolidado (`documentationStatus`) |
|---|---|---|
| `DRAFT` | (nenhum envio do tipo) | `INCOMPLETE` |
| `SUBMITTED` / `UNDER_REVIEW` | `SUBMITTED` | `PENDING_REVIEW` |
| `APPROVED` | `APPROVED` | `APPROVED` (ou `EXPIRED` se a CNH venceu) |
| `REJECTED` | `REJECTED` + `rejectionReason` | `REJECTED` |

### Swagger
- `/swagger-ui.html` e `/v3/api-docs` agora exigem login de **ADMIN** em `/docs/login`. Isso não afeta o app; afeta só quem usa a documentação.

## 9. Quarto ciclo (pendências de backend dos cards, 11/10/2026)

### ⚠️ Mudanças que o app precisa tratar
11. **Pix nas preferências com o Pix desligado → 400** *"Pix indisponível no momento. Escolha pagamento em dinheiro."* (`PAYMENT_PIX_ENABLED=false`, padrão do piloto). Antes o PUT aceitava e o erro só aparecia no `confirm-payment` (409). O app não deve oferecer Pix nas preferências do piloto.

### Esqueci minha senha (sem sessão e sem admin)
1. `POST /auth/phone/challenge` com corpo `{"purpose":"PASSWORD_RESET"}` (sem corpo, ou `NEW_PHONE`, segue sendo o desafio de cadastro/troca de número; `purpose` desconhecido → 400).
2. O usuário envia o código pelo WhatsApp **do número da conta**. Polling em `GET /auth/phone/challenge/{id}`: número sem conta → `REJECTED` com `rejectionReason=PHONE_NOT_REGISTERED`.
3. `VERIFIED` → `POST /auth/password/reset` com `{"verificationToken","newPassword"}` (senha 8–72) → **204**. As sessões anteriores são revogadas (401 do item 6) e o bloqueio por tentativas de login é zerado. O app segue para o login.
- Token de recuperação não cadastra conta nem troca telefone, e token de cadastro não redefine senha: os dois casos → 400. Token vencido ou já usado → 400.
- Conta criada pelo Google (sem senha) ganha senha e passa a entrar das duas formas.
- O reset pelo admin (`/admin/users/{id}/reset-password`) continua para quem perdeu o número.