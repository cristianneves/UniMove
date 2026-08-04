# Verificação de telefone via WhatsApp

> **TL;DR para quem só quer rodar:** `channel=LOG` é o padrão. `docker compose up -d postgres` + `./mvnw spring-boot:run` e o fluxo inteiro funciona local, sem conta na Meta, sem túnel, sem chip. Use a **seção 0 do [`api.http`](api.http)**. O resto deste documento só interessa a quem for mexer na integração com a Meta.

**Status:** implementado e **validado ponta a ponta com WhatsApp real** em 03/08/2026 — mensagem enviada de um celular brasileiro para o número de teste da Meta, webhook recebido, conta criada com o telefone entregue pela Meta.

O cadastro na UniMove só conclui depois que o usuário prova posse do telefone. A prova acontece pela **WhatsApp Cloud API oficial em fluxo reverso**: quem envia a mensagem é o usuário, não o backend.

---

## 1. Por que fluxo reverso

O OTP tradicional (backend envia o código) custa dinheiro na Cloud API — template `authentication` sai a ~R$0,15–0,19 por mensagem entregue no Brasil. O fluxo reverso não gera cobrança **nenhuma**, e não por promoção ou quota de teste, mas por regra permanente da Meta:

| Quem inicia | Template? | Custo |
|---|---|---|
| Empresa manda primeiro (OTP clássico) | obrigatório | ~R$0,17/msg |
| **Usuário manda primeiro** | não | **R$ 0,00** |
| Mensagem que a empresa **recebe** | n/a | **R$ 0,00** |

Desde 01/11/2024 a janela de serviço de 24h (aberta quando o usuário escreve para a empresa) é gratuita e ilimitada. Como nunca enviamos nada, nunca há cobrança — e não há quota que expire.

Três ganhos além do custo:

1. **Prova mais forte.** O telefone gravado é o `wa_id` que a Meta entrega no webhook, não um campo do formulário. Ninguém cadastra o número de outra pessoa.
2. **Menos atrito.** Um toque em "enviar" — sem decorar e digitar 6 dígitos.
3. **Zero infraestrutura.** A Meta hospeda; não há VPS, fila nem sessão de WhatsApp para manter.

Como corolário, os **limites de mensagens da Meta não nos afetam** (restringem conversas iniciadas pela empresa) e a **allow-list do número de teste também não** (restringe para quem a empresa pode enviar).

### Por que não Evolution API / Baileys

Evolution API é open-source e, no modo Baileys, envia WhatsApp de graça por um chip comum. Foi **descartado como canal de produção**:

- Baileys usa engenharia reversa do WhatsApp Web e **viola os Termos** da Meta.
- O banimento atinge **a conta WhatsApp do número**, sem aviso e frequentemente em definitivo. O tráfego de OTP é o pior perfil possível para o detector: chip novo, mensagens para desconhecidos, ninguém responde, texto idêntico em massa.
- Se o número cai, **o cadastro do app inteiro para** até comprar chip novo, reescanear o QR e reimplantar.
- Some-se o custo de manter um VPS, a sessão que cai e o protocolo que a Meta muda sem avisar.

Trocar um risco existencial no funil de aquisição por ~R$0 de economia (o oficial já é grátis neste fluxo) não se justifica.

---

## 2. O fluxo

```
1. POST /auth/phone/challenge          (público, sem body)
   -> { challengeId, code, waLink, expiresAt }
      waLink = https://wa.me/<numero-unimove>?text=UNIMOVE-4F2K9A
      O backend NÃO envia nada aqui.

2. [usuário toca no link e envia a mensagem pelo WhatsApp]

3. Meta -> POST /webhooks/whatsapp     (valida X-Hub-Signature-256)
   from="5574999998888", text="UNIMOVE-4F2K9A"
   -> casa o desafio, grava verified_phone = from, emite verificationToken

4. GET /auth/phone/challenge/{challengeId}    (polling do app, ~2s)
   -> { status: VERIFIED, phone: "(74) 9****-8888", verificationToken }

5. POST /auth/register { ..., verificationToken }
   -> conta criada com phone = o telefone da Meta, phone_verified_at = agora
   -> 201 + JWT
```

**Polling e não SSE**, apesar de o resto do sistema usar SSE (`/rides/*/status-stream`, `/chat/*/stream`): aqueles são autenticados, este é obrigatoriamente pré-autenticação, e manter conexões abertas sem autenticação seria superfície de exaustão de conexões. A espera aqui é de segundos.

### Estados do desafio

`PENDING` → `VERIFIED` → `CONSUMED`, com dois desvios:

- `REJECTED` + `rejectionReason=PHONE_IN_USE` — o número já tem conta; o app deve mandar o usuário para o login.
- `EXPIRED` — venceu antes de a mensagem chegar.

---

## 3. Mapa do código

Pacote `com.unimove.domain.verification`:

| Arquivo | Papel |
|---|---|
| `PhoneVerificationService` | Núcleo: cria desafio, casa código do webhook, consome token |
| `PhoneVerificationController` | `POST /auth/phone/challenge`, `GET /auth/phone/challenge/{id}` |
| `WhatsAppWebhookController` | `GET` handshake + `POST` recebimento, com validação HMAC |
| `PhoneVerification` / `...Repository` | Entidade e persistência do desafio |
| `PhoneRegistry` | **Porta** declarada aqui, implementada por `domain.user` |
| `channel/PhoneVerificationChannel` | Interface; impls `WhatsAppLinkChannel` e `LogOnlyChannel` |
| `channel/ChallengeMessage` | Formato `UNIMOVE-XXXXXXXX`; monta e extrai o código |
| `ChallengeRateLimiter` | Teto por IP, no molde de `LoginAttemptService` |
| `PhoneVerificationScheduler` | Expira vencidos e purga terminais > 24h |

**Sobre a porta `PhoneRegistry`:** a verificação precisa saber se um telefone já tem conta, mas `domain.user` já depende de `PhoneVerificationService` no cadastro. Se a verificação importasse `UserRepository`, os pacotes ficariam em ciclo. Por isso a interface é **declarada em `domain.verification`** e implementada por `UserPhoneRegistry` em `domain.user` — a dependência corre só num sentido (`user → verification`), respeitando a regra dura do `CLAUDE.md`.

Fora do pacote: `V19__phone_verification.sql`, `User.phoneVerifiedAt`, `RegisterRequest.verificationToken`, e `/webhooks/whatsapp` no `permitAll` do `SecurityConfig`.

---

## 4. Modelo mental: o webhook é código, não um serviço

Esta é a confusão mais comum, então vale explicitar.

`POST /webhooks/whatsapp` é **uma rota da aplicação**, igual a `/auth/register`. Roda em qualquer máquina que rode a app. O que não é local é o **endereço público** por onde a Meta consegue alcançá-la — a Meta está na internet e não enxerga o seu `localhost`.

A pergunta que resolve tudo: **quem chama essa rota?**

```
PRODUÇÃO
  celular do usuário ──WhatsApp──► Meta ──HTTPS──► backend hospedado
                                                   POST /webhooks/whatsapp

DESENVOLVIMENTO (channel=LOG)
  você (curl / api.http) ──────────────────────► backend na sua máquina
                                                   POST /webhooks/whatsapp

INTEGRAÇÃO REAL RODANDO LOCAL
  seu celular ──► Meta ──► túnel ──► backend na sua máquina
```

**Os três executam o mesmo código.** Muda só quem bate na porta.

O túnel não faz parte do produto: é uma ponte temporária para dar endereço público a uma máquina atrás de NAT. A mesma necessidade existe em qualquer webhook (Stripe, GitHub, Mercado Pago).

---

## 5. Como testar — escolha o modo

| Quero… | Preciso de | Esforço |
|---|---|---|
| **Testar a feature** (lógica, TTL, uso único, cadastro) | nada | **0** |
| Mandar WhatsApp real contra um **ambiente hospedado** | nada — o link já funciona | **0** |
| Mandar WhatsApp real e cair **na minha máquina** | app próprio na Meta + número de teste + túnel + `subscribed_apps` | ~15 min, 1× |

### Modo 1 — `channel=LOG` (padrão, use este)

```powershell
docker compose up -d postgres
./mvnw.cmd spring-boot:run          # channel=LOG é o default, nem precisa de .env
```

Depois execute a **seção 0 do [`api.http`](api.http)** na ordem: criar desafio → simular a Meta → pegar o token → cadastrar.

O passo "simular a Meta" é só um POST no seu próprio endpoint:

```
POST /webhooks/whatsapp
{ "entry":[{"changes":[{"value":{"messages":[
    { "from":"5574999990000", "text":{"body":"UNIMOVE-<code>"} }
]}}]}] }
```

O backend não sabe (nem precisa saber) se isso veio da Meta ou do seu terminal. Em `LOG` a validação de assinatura é pulada justamente para permitir isso. **Verificação completa, sem internet e sem conta na Meta.**

O que este modo **não** cobre: o trecho `celular → Meta → internet → sua máquina`. Ou seja, valida a nossa lógica, não a entrega da Meta — que já foi validada uma vez e não precisa ser reprovada por cada dev.

### Modo 2 — WhatsApp real contra ambiente hospedado

Se existe um staging/produção com o webhook configurado, qualquer pessoa pega o celular, abre o `waLink` e vê a conta ser criada. Zero instalação — bom inclusive para demo a investidor.

Limitação: o código que roda é o **publicado**, não o que o dev tem em aberto.

### Modo 3 — WhatsApp real contra a própria máquina

Só para quem for mexer no `WhatsAppWebhookController` ou depurar entrega da Meta. Ver seção 7.

**Cada app da Meta tem uma única URL de callback**, então dois devs não compartilham: o segundo sobrescreve o primeiro. Cada um precisa do **próprio app + número de teste** (grátis), o que dá uma WABA isolada — e cada um refaz o `subscribed_apps` (7.5b) na sua própria WABA.

---

## 6. Configuração

```yaml
app:
  phone-verification:
    channel: ${PHONE_VERIFICATION_CHANNEL:LOG}      # LOG | WHATSAPP
    challenge-ttl-minutes: ${PHONE_CHALLENGE_TTL_MINUTES:10}
    token-ttl-minutes: ${PHONE_TOKEN_TTL_MINUTES:15}
    max-challenges-per-ip-hour: ${PHONE_MAX_CHALLENGES_PER_IP_HOUR:20}
  whatsapp:
    business-number: ${WHATSAPP_BUSINESS_NUMBER:}   # E.164 sem '+'
    app-secret: ${WHATSAPP_APP_SECRET:}
    webhook-verify-token: ${WHATSAPP_WEBHOOK_VERIFY_TOKEN:}
```

Com `channel=WHATSAPP` e credencial faltando, o **startup falha** — mesma postura de `JwtService.assertSecretSafeInProd()`, para não descobrir o problema só quando o primeiro usuário tentar se cadastrar.

Note que a app **nunca chama a Graph API** e por isso **não usa token de acesso**. O único segredo em runtime é o `app-secret`, e serve só para conferir o HMAC do que chega.

---

## 7. Ligando no WhatsApp real

**Não é preciso comprar chip para testar.** A Meta fornece um número de teste grátis. E no fluxo reverso o celular pessoal é o **remetente** — papel sem restrição alguma, que nunca é registrado na API. (Nunca use um número pessoal como número *da empresa*: registrá-lo na Cloud API o desvincula do app WhatsApp comum e o histórico se perde.)

Ordem importa: a Meta valida a URL do webhook no instante em que você salva, então a app precisa já estar no ar. Por isso o webhook é o passo 5.

### 7.1 Criar o app

[developers.facebook.com/apps](https://developers.facebook.com/apps/) → **Criar app** → caso de uso *"Conectar-se a clientes via WhatsApp"* (tipo **Business**) → adicionar o produto **WhatsApp**.

Requer conta no [Meta Business](https://business.facebook.com/). Não precisa de CNPJ nem verificação de negócio para o número de teste.

O painel abre um assistente ("Etapa 1. Experimente"). Ele serve para você ver o payload no navegador — **não configura o webhook**. Ver 7.5b.

### 7.2 Número de teste

Em **WhatsApp → Configuração da API**, o campo *"De"* mostra o número de teste. Copie só os dígitos (E.164 sem `+`) → `WHATSAPP_BUSINESS_NUMBER`.

Anote também o **WhatsApp Business Account ID (WABA_ID)** e o token temporário — usados só em 7.5b.

Liberar seu celular em *"Para"* é opcional para nós: aquela allow-list governa envio, e nós só recebemos.

### 7.3 App Secret

**Configurações do app → Básico → Chave secreta do app → Mostrar** → `WHATSAPP_APP_SECRET`.

### 7.4 Expor em HTTPS público e subir a app

A Meta exige TLS válido — `localhost` não serve e autoassinado é recusado.

```powershell
winget install --id Cloudflare.cloudflared
cloudflared tunnel --url http://localhost:8080
```

Imprime `https://<algo>.trycloudflare.com`, sem exigir conta. ⚠️ **A URL muda a cada reinício** — ver 7.7 para URL fixa.

Com a URL em mãos, suba a app:

```powershell
$env:PHONE_VERIFICATION_CHANNEL   = "WHATSAPP"
$env:WHATSAPP_BUSINESS_NUMBER     = "15550001234"
$env:WHATSAPP_APP_SECRET          = "<app secret>"
$env:WHATSAPP_WEBHOOK_VERIFY_TOKEN= "unimove-webhook-2026"   # valor livre
./mvnw.cmd spring-boot:run
```

### 7.5 Configurar o webhook

Em **WhatsApp → Configuração → Webhook → Editar** (ou menu **Webhooks** → dropdown *Conta do WhatsApp Business*):

- **URL de callback:** `https://<algo>.trycloudflare.com/webhooks/whatsapp`
- **Token de verificação:** o valor de `WHATSAPP_WEBHOOK_VERIFY_TOKEN`
- **Verificar e salvar** → a Meta dispara o `GET` de handshake; o backend responde sozinho.

Depois, **assine o campo `messages`**. E deixe o app em **modo Live** — em Dev parte dos webhooks não é entregue.

As telas de *permissões* (`whatsapp_business_messaging` etc.) **não precisam de nada**: governam chamadas à Graph API, que não fazemos.

### 7.5b Inscrever a WABA no seu app ⚠️

**São três assinaturas, não duas.** Esta terceira é a que trava todo mundo, porque o painel não a mostra:

| # | Assinatura | Onde |
|---|---|---|
| 1 | URL de callback + verify token | painel |
| 2 | App inscrito no campo `messages` | painel |
| 3 | **WABA inscrita no seu app** | só via API |

Sem a 3, a Meta entrega os eventos ao *"WA DevX Webhook Events 1P App"* — o app interno dela que alimenta a tela "Experimente". O sintoma é cruel: **o payload aparece perfeito no navegador, com número e código certos, e nada chega no backend.** Parece webhook errado; na verdade os eventos estão indo para outro destino.

```bash
# Quem está inscrito?
curl "https://graph.facebook.com/v21.0/<WABA_ID>/subscribed_apps" \
  -H "Authorization: Bearer <TOKEN>"

# Inscrever o seu app
curl -X POST "https://graph.facebook.com/v21.0/<WABA_ID>/subscribed_apps" \
  -H "Authorization: Bearer <TOKEN>"      # -> {"success":true}
```

O token precisa de `whatsapp_business_management`. Este é o **único** momento em que se toca a Graph API — a verificação em si nunca envia nada.

**A inscrição é por WABA.** Ao trocar o número de teste pelo chip de produção, refaça com o novo `WABA_ID`.

### 7.6 Testar

```powershell
curl.exe -X POST http://localhost:8080/auth/phone/challenge
```

Abra o `waLink` no celular, envie, e consulte `GET /auth/phone/challenge/<challengeId>`. Deve vir `VERIFIED` com o token e o telefone mascarado.

### 7.7 URL estável (evitar reconfigurar toda sessão)

| Opção | URL fixa | Custo | Ressalva |
|---|---|---|---|
| **ngrok** (1 domínio estático no free) | sim | grátis | 20k req/mês, 1 GB; o interstício de navegador não afeta webhook |
| Cloudflare Tunnel nomeado | sim | grátis | exige domínio próprio na Cloudflare |
| `cloudflared tunnel --url` (quick) | **não** | grátis | URL nova a cada início — só teste pontual |
| Ambiente hospedado | sim | infra | melhor opção para o time |

```powershell
ngrok config add-authtoken <token>
ngrok http --url=seu-nome.ngrok-free.dev 8080
```

### 7.8 Diagnóstico

| Sintoma | Causa provável |
|---|---|
| Salvar o webhook falha | App fora do ar, túnel caído, ou verify token diferente |
| **Payload aparece no painel mas nada chega no backend** | **WABA não inscrita no seu app — ver 7.5b** |
| Webhook salvo mas status fica `PENDING` | Campo `messages` não assinado, ou app em modo Dev |
| Log diz "assinatura invalida" | `WHATSAPP_APP_SECRET` errado |
| Status vira `REJECTED`/`PHONE_IN_USE` | Aquele número já tem conta — comportamento correto |

Suba com `LOGGING_LEVEL_COM_UNIMOVE_DOMAIN_VERIFICATION=DEBUG`. Em `INFO`, uma reentrega ou um código desconhecido passam silenciosos, e **"não chegou" fica indistinguível de "chegou e foi ignorado"** — distinção essencial para diagnosticar.

---

## 8. Hospedagem

Qualquer host serve, desde que ofereça **endereço público + HTTPS com certificado válido**. O webhook é só mais uma rota do mesmo backend que o app Flutter já consome — **não há serviço separado para hospedar**.

### Railway (ou PaaS equivalente)

URL HTTPS sai pronta. Configure na Meta:

```
https://<app>.up.railway.app/webhooks/whatsapp
```

### VPS

| Requisito | Detalhe |
|---|---|
| IP público | toda VPS tem |
| **Domínio** | obrigatório — a Meta não aceita IP puro, não existe certificado válido para IP |
| **TLS válido** | Let's Encrypt; **autoassinado é recusado** |
| Porta 443 aberta | firewall / security group |
| Proxy reverso → `:8080` | Caddy ou nginx |

Caddy resolve o certificado sozinho, sem certbot nem cron de renovação:

```caddy
api.unimove.com.br {
    reverse_proxy localhost:8080
}
```

E aponte o webhook para `https://api.unimove.com.br/webhooks/whatsapp`.

---

## 9. Decisões de segurança

**O `code` não é credencial.** A prova de posse é o remetente que a Meta assina e entrega; o código só liga a mensagem recebida ao desafio. Por isso fica em claro no banco. São 8 caracteres de um alfabeto sem ambíguos (32⁸ ≈ 1,1 × 10¹²).

**O `verificationToken` é credencial portadora** — mas também fica em claro, de propósito. Hashear só protegeria contra quem já tem dump do banco, e esse atacante já domina todas as contas: o ganho seria nulo. A defesa real é o TTL de 15 min e o uso único. Existe separado do `challengeId` para não trafegar em URL, já que path costuma cair em log de proxy.

**O webhook é o único ponto que aprende um telefone verificado**, e é público. Valida `X-Hub-Signature-256` (HMAC-SHA256 do corpo cru com o app secret) **antes de qualquer parsing**, com comparação em tempo constante. Quando a assinatura confere, responde **sempre 200** — mesmo para payload irrelevante ou JSON malformado —, porque qualquer outro status faz a Meta reenviar em loop.

**Consumo dentro da transação do cadastro.** Se o register falhar depois (e-mail duplicado, cidade inválida), o rollback devolve o token e o usuário corrige o formulário sem reverificar. Comportamento desejado, coberto por teste.

**Telefone imutável.** `PUT /users/me` não edita o telefone — verificar no cadastro e permitir troca livre depois tornaria a verificação decorativa. Troca de número passa pelo admin, mesmo caminho do reset de senha do MVP.

**Rate limit por IP** (`ChallengeRateLimiter`, 20/hora). Pouco crítico por construção: criar desafio não envia mensagem, então abusar custa linha em tabela, não dinheiro.

### Limitações conhecidas

**`X-Forwarded-For` é confiável demais.** `PhoneVerificationController.clientIp()` lê o primeiro item do header. Com a config padrão de nginx (`$proxy_add_x_forwarded_for`) e de Caddy, o header **acrescenta** ao que o cliente mandou — então um cliente pode forjar o primeiro salto e **furar o rate limit** trocando o header a cada requisição.

Impacto baixo (o abuso não gera custo), mas é furo real. Correções possíveis:
- no proxy: `proxy_set_header X-Forwarded-For $remote_addr;`
- no código: `ForwardedHeaderFilter` com lista de proxies confiáveis, ou ler o último salto.

**Rate limit em memória.** Com múltiplas réplicas o contador passa a ser por réplica — mesma limitação já documentada em `LoginAttemptService`.

---

## 10. Produção — checklist

- [ ] **Chip dedicado** (o número de teste só conversa com os 5 da allow-list). Não pode estar em uso em nenhum WhatsApp; evite número reciclado.
- [ ] Registrar na WABA e concluir a verificação por SMS/ligação.
- [ ] Webhook apontando para a URL definitiva (Railway ou VPS com domínio + TLS).
- [ ] **Refazer o `subscribed_apps`** com o novo `WABA_ID` (7.5b).
- [ ] App em modo Live; campo `messages` assinado.
- [ ] `WHATSAPP_APP_SECRET` de produção — nunca reaproveite um secret que circulou em chat, log ou print.
- [ ] Endurecer o `X-Forwarded-For` (seção 9).

Não é preciso criar template algum, e os limites de mensagens da Meta não se aplicam: restringem conversas iniciadas pela empresa, e nós não iniciamos nenhuma.

---

## 11. Impacto em quem consome a API

**Breaking change** em `POST /auth/register`:

- saiu o campo `phone`;
- entrou `verificationToken` (obrigatório).

`PUT /users/me` não aceita mais `phone` no body. `GET /users/me` passa a devolver `phoneVerified`.

A migration `V19` limpou a base de teste (todas as corridas e usuários não-ADMIN) antes de criar o índice único de telefone — decisão consciente, registrada no cabeçalho do arquivo.

O app Flutter vive em repositório separado e precisa acompanhar: criar o desafio, abrir o `waLink`, fazer polling do status e enviar o `verificationToken` no cadastro.
