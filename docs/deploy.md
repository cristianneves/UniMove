# Deploy — Render (API) + Supabase (Postgres)

Push na `main` → testes → imagem no GHCR → o Render puxa e sobe. Custo: **R$ 0**.

```
git push origin main
   │
   ├─ [test]   mvn -B verify ......................... ~1 min, sem Docker/Postgres
   ├─ [build]  buildx linux/amd64
   │           push ghcr.io/cristianneves/unimove:<sha> e :latest
   └─ [deploy] curl no deploy hook do Render, com imgURL=<sha>
               espera /actuator/health responder UP
```

Arquivos: [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml),
[`render.yaml`](../render.yaml), [`Dockerfile`](../Dockerfile),
[`application-prod.yml`](../src/main/resources/application-prod.yml).

---

## ⚠️ Leia antes: o que o plano gratuito custa

O Render free **derruba o servico apos 15 minutos sem requisicoes**. Isso nao e
so lentidao no primeiro acesso — enquanto dorme, o app nao roda nada:

| O que para | Consequencia |
|---|---|
| `RideExpirationScheduler` | corridas nao expiram; o mural acumula pedidos velhos |
| `DriverAutoOfflineScheduler` | motorista sumido continua marcado como online |
| `PhoneVerificationScheduler` | desafios expirados nao sao limpos |
| Conexoes SSE | mural, chat e status caem a cada hibernacao |

E o primeiro acesso depois de dormir leva **30–60 s** (a JVM sobe do zero).

Serve para **demonstrar** o backend e para o dev mobile integrar. Para o piloto
real em Remanso isso nao se sustenta — nesse momento a saida e um plano pago
(Render Starter ~US$7/mes, sem hibernacao) ou uma VPS, e o
[`docker-compose.prod.yml`](../docker-compose.prod.yml) ja esta pronto para isso.

---

## 1. Banco no Supabase

1. Crie um projeto em 👉 https://supabase.com/dashboard
   - **Region: `East US (North Virginia)`** — a mesma do servico no Render. Cada
     request faz varias idas ao banco, entao a distancia ate ele pesa mais que a
     do usuario ate a API.
   - Guarde a senha do banco que ele gera.

2. **Feche a API REST do Supabase** — passo obrigatorio, nao opcional.
   O Supabase publica automaticamente as tabelas do schema `public` como uma API
   REST (PostgREST) protegida por RLS. Nossas tabelas sao criadas pelo Flyway
   **sem RLS**, entao ficariam legiveis por qualquer um com a chave `anon`, que e
   publica: telefones, corridas, mensagens de chat.

   👉 *Project Settings → API → Exposed schemas* → **remova `public`**, deixe a
   lista vazia (ou so `graphql_public`). Nos usamos o Postgres direto, nunca a
   API do Supabase.

   Confira depois do primeiro deploy:
   ```bash
   curl "https://<project-ref>.supabase.co/rest/v1/users?apikey=<anon-key>"
   # tem que dar erro de schema desconhecido, NUNCA uma lista de usuarios
   ```

3. Pegue a string de conexao em *Connect → Session pooler* (**nao** use "Direct
   connection": ela e IPv6-only e o Render nao alcanca; e **nao** use Transaction
   mode na 6543, que quebra prepared statements do Hibernate).

   Da string `postgresql://postgres.abcdefgh:SENHA@aws-0-us-east-1.pooler.supabase.com:5432/postgres`
   saem tres variaveis:

   | Variavel | Valor |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require` |
   | `DATABASE_USER` | `postgres.abcdefgh` (com o project-ref!) |
   | `DATABASE_PASSWORD` | a senha do banco |

> O projeto free **pausa apos 7 dias sem nenhuma atividade** e so volta
> manualmente pelo dashboard. Como o Render tambem hiberna, isso pode acontecer
> numa semana parada.

## 2. Credencial do GHCR no Render

A imagem e privada. Crie um token em 👉 https://github.com/settings/tokens/new
com **apenas** o escopo `read:packages`.

No Render: 👉 https://dashboard.render.com/settings/registry-credentials →
*Add Credential*

- **Name:** `ghcr-unimove` ← precisa ser exatamente isso (o `render.yaml` referencia por nome)
- **Registry:** GitHub Container Registry
- **Username:** `cristianneves` · **Password:** o token

## 3. Publicar a primeira imagem

O Render so consegue criar o servico se a imagem ja existir. Rode o workflow uma
vez a mao antes de criar o Blueprint:

👉 https://github.com/cristianneves/UniMove/actions/workflows/deploy.yml →
*Run workflow* → branch `main`

O job `deploy` vai falhar (o servico ainda nao existe) — normal. O que importa e
o job `build` ter publicado a imagem. Confira em
👉 https://github.com/cristianneves?tab=packages

## 4. Criar o servico no Render

👉 https://dashboard.render.com/blueprints → *New Blueprint Instance* → aponte
para `cristianneves/UniMove`, branch `main`.

Ele le o [`render.yaml`](../render.yaml) e pergunta os valores marcados como
`sync: false`. Preencha:

| Variavel | Valor |
|---|---|
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | os do passo 1 |
| `JWT_SECRET` | `openssl rand -base64 48` |
| `WHATSAPP_*` | deixe **vazio** por enquanto |
| `GOOGLE_CLIENT_IDS` | deixe **vazio** por enquanto |

> Suba com `PHONE_VERIFICATION_CHANNEL=LOG` e `GOOGLE_LOGIN_ENABLED=false` (ja e
> o default no `render.yaml`). Ligar os dois de cara com credencial faltando
> **derruba o startup de proposito**, e voce nao vai saber se o problema e o
> deploy ou a Meta.

No primeiro deploy o Flyway aplica as 20 migrations no Supabase. Acompanhe pela
aba *Logs* do servico.

## 5. Secrets no GitHub

👉 https://github.com/cristianneves/UniMove/settings/secrets/actions

| Secret | Onde achar |
|---|---|
| `RENDER_DEPLOY_HOOK` | Render → servico → *Settings* → **Deploy Hook** (URL com `?key=`) |
| `RENDER_SERVICE_URL` | a URL publica, ex. `https://unimove-api.onrender.com` (sem barra no fim) |

A partir daqui, todo push na `main` faz deploy sozinho.

## 6. Verificar

```bash
curl https://unimove-api.onrender.com/actuator/health    # {"status":"UP"}
```

Rode o fluxo de [`api.http`](./api.http) contra a URL publica: register (com
`verificationToken`), login, `/rides/estimate`, `POST /rides`, aceitar como
motorista.

**Troque a senha do admin** semeado em `V2__seed_admin.sql` — o hash BCrypt esta
versionado neste repositorio.

## 7. Depois: WhatsApp e Google

**WhatsApp** 👉 https://developers.facebook.com/apps → seu app → WhatsApp →
*Configuration*
- Callback URL: `https://unimove-api.onrender.com/webhooks/whatsapp`
- Verify token: o mesmo valor de `WHATSAPP_WEBHOOK_VERIFY_TOKEN`
- Preencha as tres vars no Render e mude `PHONE_VERIFICATION_CHANNEL` para `WHATSAPP`

> A Meta reenvia o webhook algumas vezes, mas se o servico estiver hibernando a
> primeira tentativa pode expirar. Detalhes em [`verificacao-telefone.md`](./verificacao-telefone.md).

**Google** 👉 https://console.cloud.google.com/apis/credentials — um OAuth client
por plataforma; liste os client IDs separados por virgula em `GOOGLE_CLIENT_IDS`
e ponha `GOOGLE_LOGIN_ENABLED=true`. Ver [`login-social-google.md`](./login-social-google.md).

---

## Operacao

**Rollback:** reenvie o deploy hook apontando para um SHA anterior —
```bash
curl -G "<RENDER_DEPLOY_HOOK>" --data-urlencode "imgURL=ghcr.io/cristianneves/unimove:<sha-antigo>"
```
ou use *Rollback* no dashboard do Render.

> Migration de banco **nao** volta com o rollback. Se a versao quebrada rodou uma
> `V{n}`, o schema fica na frente do codigo antigo — corrija com uma `V{n+1}`
> nova, nunca editando a migration ja aplicada.

**Qual versao esta no ar:** a aba *Events* do servico mostra o `imgURL` de cada deploy.

**Backup:** o Supabase free faz backup diario automatico (retencao curta). Para
uma copia sua:
```bash
pg_dump "postgresql://postgres.<ref>:<senha>@aws-0-us-east-1.pooler.supabase.com:5432/postgres" \
  | gzip > unimove-$(date +%F).sql.gz
```

---

## Limites conhecidos

| Limite | Por que | Quando resolver |
|---|---|---|
| Hiberna em 15 min / cold start 30–60 s | plano free do Render | ao sair do modo demo |
| Schedulers param enquanto hiberna | idem | idem |
| 512 MB de RAM | plano free | JVM ja calibrada (SerialGC, heap 50%, pool de 5) |
| Instancia unica | hubs de SSE em memoria (`analise-mvp.md`) | ao escalar: pub/sub via Redis |
| Supabase pausa com 7 dias parado | plano free | despausar no dashboard |
| Sem staging | um ambiente so, como no MVP | antes de abrir para usuarios reais |
| OSRM na demo publica | `router.project-osrm.org`, sem SLA | self-host |
