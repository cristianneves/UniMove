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

---

## Ativando em produção

1. **Chip dedicado.** Um número que **não** esteja em uso em nenhum WhatsApp (pessoal ou Business) — registrá-lo na Cloud API o desvincula do app WhatsApp comum. Evite número reciclado com histórico.
2. **Meta Business** → criar app do tipo Business em `developers.facebook.com` → adicionar o produto **WhatsApp**.
3. Registrar o número na WhatsApp Business Account e concluir a verificação por SMS/ligação que a Meta faz uma vez.
4. **Webhook**: apontar para `https://<host>/webhooks/whatsapp`, definir o *verify token* (mesmo valor de `WHATSAPP_WEBHOOK_VERIFY_TOKEN`) e assinar o campo **`messages`**. A Meta faz um `GET` de handshake — o backend já responde.
5. Copiar o **App Secret** (Configurações → Básico) para `WHATSAPP_APP_SECRET`.
6. Definir `PHONE_VERIFICATION_CHANNEL=WHATSAPP` e `WHATSAPP_BUSINESS_NUMBER`.

Não é preciso criar template algum, e os limites de mensagens da Meta não se aplicam: eles restringem conversas **iniciadas pela empresa**, e nós não iniciamos nenhuma.

---

## Impacto em quem consome a API

**Breaking change** em `POST /auth/register`:

- saiu o campo `phone`;
- entrou `verificationToken` (obrigatório).

`PUT /users/me` não aceita mais `phone` no body. `GET /users/me` passa a devolver `phoneVerified`.

A migration `V19` limpou a base de teste (todas as corridas e usuários não-ADMIN) antes de criar o índice único de telefone — decisão consciente, registrada no cabeçalho do arquivo.
