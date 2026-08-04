# Verificação de telefone via WhatsApp

Status: **implementado** ✅ — canal real pendente apenas de um chip dedicado (ver [Ativando em produção](#ativando-em-produção)).

O cadastro na UniMove só conclui depois que o usuário prova posse do telefone. A prova acontece pela **WhatsApp Cloud API oficial em fluxo reverso**: quem envia a mensagem é o usuário, não o backend.

---

## Por que fluxo reverso

O OTP tradicional (backend envia o código) custa dinheiro na Cloud API — template `authentication` sai a ~R$0,15–0,19 por mensagem entregue no Brasil. O fluxo reverso não gera cobrança **nenhuma**, e não por promoção ou quota de teste, mas por regra permanente da Meta:

| Quem inicia | Template? | Custo |
|---|---|---|
| Empresa manda primeiro (OTP clássico) | obrigatório | ~R$0,17/msg |
| **Usuário manda primeiro** | não | **R$ 0,00** |
| Mensagem que a empresa **recebe** | n/a | **R$ 0,00** |

Desde 01/11/2024 a janela de serviço de 24h (aberta quando o usuário escreve para a empresa) é gratuita e ilimitada. Como nunca enviamos nada, nunca há cobrança.

Três ganhos além do custo:

1. **Prova mais forte.** O telefone gravado é o `wa_id` que a Meta entrega no webhook, não um campo do formulário. Ninguém cadastra o número de outra pessoa.
2. **Menos atrito.** Um toque em "enviar" — sem decorar e digitar 6 dígitos.
3. **Zero infraestrutura.** A Meta hospeda; não há VPS, fila nem sessão para manter.

### Por que não Evolution API / Baileys

Evolution API é open-source e, no modo Baileys, envia WhatsApp de graça por um chip comum. Foi **descartado como canal de produção**:

- Baileys usa engenharia reversa do WhatsApp Web e **viola os Termos** da Meta.
- O banimento atinge **a conta WhatsApp do número**, sem aviso e frequentemente em definitivo. O tráfego de OTP é o pior perfil possível para o detector: chip novo, mensagens para desconhecidos, ninguém responde, texto idêntico em massa.
- Se o número cai, **o cadastro do app inteiro para** até comprar chip novo, reescanear o QR e reimplantar.
- Some-se o custo de manter um VPS, a sessão que cai e o protocolo que a Meta muda sem avisar.

Trocar um risco existencial no funil de aquisição por ~R$0 de economia (o oficial já é grátis neste fluxo) não se justifica.

---

## Fluxo

```
1. POST /auth/phone/challenge          (público, sem body)
   -> { challengeId, code, waLink, expiresAt }
      waLink = https://wa.me/<numero-unimove>?text=UNIMOVE-4F2K9A
      Nada é enviado pelo backend.

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

**Polling e não SSE**, apesar de o resto do sistema usar SSE: aqueles endpoints são autenticados, este é obrigatoriamente pré-autenticação, e manter conexões abertas sem autenticação seria superfície de exaustão de conexões. A espera aqui é de segundos.

### Estados do desafio

`PENDING` → `VERIFIED` → `CONSUMED`, com dois desvios:

- `REJECTED` + `rejectionReason=PHONE_IN_USE` — o número já tem conta; o app deve mandar o usuário para o login.
- `EXPIRED` — venceu antes de a mensagem chegar.

---

## Decisões de segurança

**O `code` não é credencial.** A prova de posse é o remetente que a Meta assina e entrega; o código só liga a mensagem recebida ao desafio. Por isso fica em claro no banco. São 8 caracteres de um alfabeto sem ambíguos (32⁸ ≈ 1,1 × 10¹²).

**O `verificationToken` é credencial portadora** — mas também fica em claro, de propósito. Hashear só protegeria contra quem já tem dump do banco, e esse atacante já domina todas as contas: o ganho seria nulo. A defesa real é o TTL de 15 min e o uso único. Ele existe separado do `challengeId` para não trafegar em URL, já que path costuma cair em log de proxy.

**O webhook é o único ponto que aprende um telefone verificado**, e é público. Valida `X-Hub-Signature-256` (HMAC-SHA256 do corpo cru com o app secret) **antes de qualquer parsing**, com comparação em tempo constante. Quando a assinatura confere, responde **sempre 200**, mesmo para payload que não interessa ou JSON malformado — qualquer outro status faz a Meta reenviar em loop.

**Consumo dentro da transação do cadastro.** Se o register falhar depois (e-mail duplicado, cidade inválida), o rollback devolve o token e o usuário corrige o formulário sem reverificar o telefone. Comportamento desejado, coberto por teste.

**Telefone imutável.** `PUT /users/me` não edita mais o telefone — verificar no cadastro e permitir troca livre depois tornaria a verificação decorativa. Troca de número passa pelo admin, mesmo caminho do reset de senha do MVP.

**Rate limit por IP** (`ChallengeRateLimiter`, 20/hora por padrão). Pouco crítico por construção: criar desafio não envia mensagem, então abusar custa só linha em tabela — não há SMS queimando dinheiro. O limite existe para evitar inchaço, já que `/auth/**` é público.

---

## Configuração

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

### Desenvolvimento (`channel=LOG`)

O link e o código saem no log e a validação de assinatura é pulada, então a Meta é simulada por um POST manual. O caminho exercitado é o real — o mesmo controller, o mesmo serviço. Ver seção 0 de [`api.http`](api.http).

**Este é o padrão, e é o que todo dev deve usar.** Sem conta na Meta, sem túnel, sem chip: `./mvnw spring-boot:run` e o fluxo completo funciona local. Só quem for mexer na integração com a Meta precisa do resto deste documento.

### Trabalhando em equipe

**Cada app da Meta tem uma única URL de callback** — dois devs não conseguem apontar para o mesmo app sem um sobrescrever o outro. Quem precisar testar a integração real deve criar o **próprio app + número de teste** (grátis), o que dá uma WABA isolada. Cada um precisa refazer o passo 5b na sua própria WABA.

Para não reconfigurar o webhook a cada sessão, use uma URL estável em vez do túnel efêmero:

| Opção | URL fixa | Custo | Ressalva |
|---|---|---|---|
| **ngrok** (1 domínio estático no plano free) | sim | grátis | 20k req/mês, 1 GB; o interstício de navegador não afeta webhook |
| Cloudflare Tunnel nomeado | sim | grátis | exige domínio próprio na Cloudflare |
| `cloudflared tunnel --url` (quick) | **não** | grátis | URL nova a cada início — só para teste pontual |

```powershell
ngrok config add-authtoken <token>
ngrok http --url=seu-nome.ngrok-free.dev 8080
```

Com domínio estático, o webhook é configurado na Meta **uma vez** e sobrevive a reinícios. Para validação de integração do time, o caminho mais simples é o deploy de staging, que já tem URL fixa e não depende da máquina de ninguém.

---

## Ligando no WhatsApp real (teste)

**Não é preciso comprar chip para testar.** A Meta fornece um número de teste grátis, e no fluxo reverso o seu celular pessoal é o **remetente** — papel sem restrição alguma, que nunca é registrado na API.

Cuidado com a ordem: a Meta valida a URL do webhook no momento em que você salva, então a aplicação precisa já estar no ar com o verify token configurado. Por isso o webhook é o passo 5, não o 1.

### 1. Criar o app na Meta

[developers.facebook.com/apps](https://developers.facebook.com/apps/) → **Criar app** → caso de uso *"Conectar-se a clientes via WhatsApp"* (tipo **Business**) → adicionar o produto **WhatsApp**.

Requer uma conta no [Meta Business](https://business.facebook.com/). Não é preciso CNPJ nem verificação de negócio para o número de teste.

### 2. Pegar o número de teste e liberar o seu celular

No app, em **WhatsApp → Configuração da API**:

- O campo *"De"* mostra o **número de teste**. Copie só os dígitos (E.164 sem `+`) — é o `WHATSAPP_BUSINESS_NUMBER`.
- Em *"Para"* → **gerenciar lista de números** → adicione seu celular com DDI. A Meta manda um código para ele; confirme. Cabem até **5 números**.

### 3. Copiar o App Secret

**Configurações do app → Básico → Chave secreta do app → Mostrar**. É o `WHATSAPP_APP_SECRET`, usado para validar o HMAC do webhook.

### 4. Expor a aplicação em HTTPS público

A Meta exige TLS válido — `localhost` não serve e certificado autoassinado é recusado. Para teste local, um túnel efêmero:

```powershell
winget install --id Cloudflare.cloudflared
cloudflared tunnel --url http://localhost:8080
```

Ele imprime uma URL `https://<algo>.trycloudflare.com`. Não exige conta.

⚠️ **A URL muda a cada reinício do túnel** — e aí é preciso reeditar o webhook no painel da Meta. Para algo mais estável, use o deploy do Railway.

Agora suba a aplicação com as variáveis:

```powershell
$env:PHONE_VERIFICATION_CHANNEL   = "WHATSAPP"
$env:WHATSAPP_BUSINESS_NUMBER     = "15550001234"      # numero de teste, so digitos
$env:WHATSAPP_APP_SECRET          = "<app secret>"
$env:WHATSAPP_WEBHOOK_VERIFY_TOKEN= "unimove-webhook-2026"   # valor livre, voce inventa
./mvnw.cmd spring-boot:run
```

Se faltar alguma variável, o startup falha dizendo qual — de propósito.

### 5. Configurar o webhook

Em **WhatsApp → Configuração → Webhook → Editar**:

- **URL de callback:** `https://<algo>.trycloudflare.com/webhooks/whatsapp`
- **Token de verificação:** exatamente o valor de `WHATSAPP_WEBHOOK_VERIFY_TOKEN`
- **Verificar e salvar** → a Meta dispara o `GET` de handshake; o backend responde sozinho.

Depois, em **Gerenciar** os campos do webhook, **assine o campo `messages`**.

Deixe o app em **modo Live** (chave no topo do painel): em modo Dev parte dos webhooks não é entregue.

### 5b. Inscrever a WABA no seu app ⚠️

**São três assinaturas, não duas** — e esta terceira é a que trava todo mundo, porque o painel não a mostra:

| # | Assinatura | Onde |
|---|---|---|
| 1 | URL de callback + verify token | painel |
| 2 | App inscrito no campo `messages` | painel |
| 3 | **WABA inscrita no seu app** | só via API |

Sem a 3, a Meta entrega os eventos para o *"WA DevX Webhook Events 1P App"* — o app interno dela que alimenta a tela "Experimente" do onboarding. O sintoma é cruel: o payload aparece perfeito no navegador, com o número e o código certos, **e nada chega no backend**. Parece que o webhook está errado, mas o problema é que os eventos estão indo para outro destino.

Conferir quem está inscrito:

```bash
curl "https://graph.facebook.com/v21.0/<WABA_ID>/subscribed_apps" \
  -H "Authorization: Bearer <TOKEN>"
```

Se o seu app não estiver na lista, inscreva:

```bash
curl -X POST "https://graph.facebook.com/v21.0/<WABA_ID>/subscribed_apps" \
  -H "Authorization: Bearer <TOKEN>"     # -> {"success":true}
```

O `WABA_ID` e um token temporário estão em **WhatsApp → Configuração da API**. O token precisa de `whatsapp_business_management`, e é o **único** momento em que a nossa integração toca a Graph API — a verificação em si nunca envia nada.

### 6. Testar

```powershell
curl.exe -X POST http://localhost:8080/auth/phone/challenge
```

Abra o `waLink` retornado no celular que você liberou no passo 2, envie a mensagem, e consulte:

```powershell
curl.exe http://localhost:8080/auth/phone/challenge/<challengeId>
```

Deve vir `VERIFIED` com o `verificationToken` e o seu telefone mascarado.

### Diagnóstico

| Sintoma | Causa provável |
|---|---|
| Salvar o webhook falha | App fora do ar, túnel caído, ou verify token diferente |
| **Payload aparece no painel mas nada chega no backend** | **WABA não inscrita no seu app — ver 5b** |
| Webhook salvo mas status fica `PENDING` | Campo `messages` não assinado, ou app em modo Dev |
| Backend loga "assinatura invalida" | `WHATSAPP_APP_SECRET` errado |
| Status vira `REJECTED`/`PHONE_IN_USE` | Aquele número já tem conta — comportamento correto |

Para diagnosticar, suba com `LOGGING_LEVEL_COM_UNIMOVE_DOMAIN_VERIFICATION=DEBUG`: em `INFO`, uma reentrega ou um código desconhecido passam silenciosos, e "não chegou" fica indistinguível de "chegou e foi ignorado".

---

## Produção

Trocar o número de teste por um **chip dedicado**, porque o de teste só conversa com os 5 números da allow-list.

- O chip **não** pode estar em uso em nenhum WhatsApp (pessoal ou Business): registrar na Cloud API desvincula o número do app comum, e o histórico se perde. Nunca use o número pessoal aqui.
- Evite número reciclado com histórico prévio no WhatsApp.
- Registre-o na WhatsApp Business Account e conclua a verificação por SMS/ligação.
- Aponte o webhook para a URL do Railway em vez do túnel.

Não é preciso criar template algum, e os limites de mensagens da Meta não se aplicam: eles restringem conversas **iniciadas pela empresa**, e nós não iniciamos nenhuma.

---

## Impacto em quem consome a API

**Breaking change** em `POST /auth/register`:

- saiu o campo `phone`;
- entrou `verificationToken` (obrigatório).

`PUT /users/me` não aceita mais `phone` no body. `GET /users/me` passa a devolver `phoneVerified`.

A migration `V19` limpou a base de teste (todas as corridas e usuários não-ADMIN) antes de criar o índice único de telefone — decisão consciente, registrada no cabeçalho do arquivo.
