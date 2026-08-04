# Login social com Google

Como funciona, por que funciona assim, e o que precisa ser configurado para ligar.

---

## 1. O problema que isso resolve

Até aqui só havia um jeito de entrar: e-mail + senha. Isso cobra dois pedágios do usuário no funil de cadastro — inventar e lembrar de uma senha, e depender de um admin (`POST /admin/users/{id}/reset-password`) quando esquecer. Login com Google elimina os dois.

**O que o Google NÃO substitui: o telefone.** A verificação de posse do número pelo WhatsApp continua obrigatória no cadastro social. Num app de corridas, passageiro e motorista precisam de um número real e verificado — é o que sustenta o contato entre eles e boa parte do antifraude. O Google prova quem é o dono do e-mail; o WhatsApp prova quem é o dono do telefone. São coisas diferentes e as duas são necessárias.

---

## 2. Por que ID Token e não redirect OAuth

O app Flutter faz o sign-in nativo (`google_sign_in`) e manda para o backend apenas o `id_token` resultante. O backend valida a assinatura contra o JWKS público do Google e emite o **mesmo** JWT de sempre.

O que isso nos poupa:

| Redirect (Authorization Code)      | ID Token (o que fizemos)              |
|------------------------------------|---------------------------------------|
| `spring-boot-starter-oauth2-client` | só `spring-security-oauth2-jose`      |
| `client_secret` guardado no servidor | nenhum segredo do Google no backend   |
| Callback URL, sessão, state/PKCE   | stateless, um POST                    |
| WebView no app (UX ruim no mobile) | sign-in nativo do sistema             |

O redirect só valeria a pena se houvesse um painel **web** fazendo login social. Não há.

---

## 3. O fluxo

### Usuário que já existe

```
app → sign-in nativo do Google → id_token
app → POST /auth/social {provider, idToken}
   ← 200 {status: "AUTHENTICATED", auth: {token, userId, role, cidade, expiresAt}}
```

### Primeiro acesso

```
app → POST /auth/social {provider, idToken}
   ← 200 {status: "REGISTRATION_REQUIRED", profile: {email, name}}

app → POST /auth/phone/challenge          (desafio do WhatsApp, igual ao cadastro comum)
usuário → manda "UNIMOVE-XXXXXXXX" no WhatsApp
app → GET /auth/phone/challenge/{id}      (polling até VERIFIED → verificationToken)

app → POST /auth/social/register {provider, idToken, verificationToken, role, cidade, ...}
   ← 201 {token, userId, role, cidade, expiresAt}
```

**Por que a resposta do `/auth/social` é 200 discriminada e não 404:** usuário novo não é erro, é um caminho previsto. O app tem que ramificar de qualquer jeito, e o `profile` já vem pronto para pré-preencher o formulário.

**Por que o `idToken` é reenviado no register:** ele já é assinado pelo Google e já tem validade própria (~1h). Criar um "ticket de cadastro" com tabela, token e TTL no nosso banco seria reimplementar, pior, o que o token do Google já faz. Zero estado novo no servidor.

---

## 4. Vinculação de contas

Se o e-mail do Google já existe como conta com senha, a identidade é **vinculada automaticamente** e a conta passa a aceitar os dois métodos de entrada.

Isso só é seguro por causa de uma condição, verificada em `SocialAuthService.verifyIdentity`: o token precisa trazer `email_verified=true`. Sem essa checagem, quem conseguisse um provedor que emite tokens com e-mail arbitrário assumiria qualquer conta do sistema. Token sem e-mail verificado leva **403** e não encosta em nenhum registro.

O `subject` (o `sub` do Google) é a chave guardada em `social_identities`, não o e-mail — o e-mail de uma conta Google pode mudar, o `sub` não.

---

## 5. Contas sem senha

`users.password_hash` virou nullable na `V20`. Consequências deliberadas:

| Situação | Comportamento |
|---|---|
| `POST /auth/login` com senha numa conta só-social | **401 genérico**, o mesmo de senha errada. Dizer "esta conta usa Google" seria enumeração de usuário. |
| `PUT /users/me/password` numa conta só-social | **409** com mensagem explicando que não há senha. Aqui o usuário já está autenticado, então a mensagem específica não vaza nada — e sem ela o erro seria "senha atual incorreta", mandando o usuário procurar uma senha que nunca existiu. |
| `POST /admin/users/{id}/reset-password` | Funciona normalmente e **converte a conta em dual** (Google + senha). É o caminho para quem perder o acesso à conta Google. |
| `GET /users/me` | Traz `hasPassword: false`, para o app esconder "trocar senha". |

---

## 6. Configuração

### 6.1 No Google Cloud Console

1. Crie (ou reaproveite) um projeto em <https://console.cloud.google.com>.
2. **APIs & Services → OAuth consent screen**: tipo **External**, preencha nome do app, e-mail de suporte e domínio. Para o piloto basta o modo *Testing* com os testadores cadastrados; publicar exige a tela de consentimento revisada.
3. **APIs & Services → Credentials → Create credentials → OAuth client ID**, um por plataforma:

| Plataforma | Tipo do client | O que pede |
|---|---|---|
| Android | Android | package name + SHA-1 do certificado de assinatura (debug **e** release são SHA-1 diferentes — cadastre os dois) |
| iOS | iOS | bundle ID |
| Backend/servidor | Web application | nada além do nome |

### 6.2 Por que `GOOGLE_CLIENT_IDS` é uma lista

O claim `aud` do `id_token` **muda conforme a plataforma**:

- Android, com `serverClientId` configurado no `google_sign_in`: o `aud` é o **client ID Web**.
- iOS: o `aud` é o **client ID iOS**.

Aceitar um único valor funciona no primeiro dia e quebra silenciosamente quando a segunda plataforma entra. Por isso a configuração é uma lista separada por vírgula, e a checagem aceita qualquer um dos IDs cadastrados.

Essa validação de audiência **não é opcional**: sem ela, um `id_token` legítimo emitido para *outro* app Google qualquer seria aceito aqui — e quem controla aquele app controla o e-mail dentro do token.

### 6.3 Variáveis de ambiente

```bash
GOOGLE_LOGIN_ENABLED=true
GOOGLE_CLIENT_IDS=111-web.apps.googleusercontent.com,222-ios.apps.googleusercontent.com
# GOOGLE_JWK_SET_URI só muda em teste; o default aponta para o JWKS do Google
```

Com `GOOGLE_LOGIN_ENABLED=false` (o default) nenhum bean nasce e os dois endpoints respondem **503**. É isso que permite dev e testes rodarem sem nenhuma credencial. Se ligar sem preencher `GOOGLE_CLIENT_IDS`, o startup falha de propósito — melhor quebrar no boot do que no primeiro login de produção.

---

## 7. Lado do app Flutter

```dart
final googleSignIn = GoogleSignIn(
  // Faz o Android emitir id_token com aud = client ID WEB.
  serverClientId: '111-web.apps.googleusercontent.com',
  scopes: ['email', 'profile'],
);

final account = await googleSignIn.signIn();
final auth = await account!.authentication;
final idToken = auth.idToken; // é ISTO que vai para o backend
```

Mande `idToken`, nunca `accessToken` — o access token não é assinado nem verificável offline.

---

## 8. Erros

| HTTP | Quando |
|---|---|
| 400 | payload inválido (falta `verificationToken`, `role=ADMIN`, motorista sem veículo) |
| 401 | `id_token` com assinatura, emissor, audiência ou validade que não conferem |
| 403 | `email_verified=false` no provedor |
| 409 | no `/auth/social/register`, a conta já existe — o app deve refazer `POST /auth/social`, que autentica (e vincula, se for o caso) |
| 503 | login social não configurado neste ambiente |

Não há lockout por tentativas nestes endpoints, e é proposital: não existe força bruta contra um token assinado pelo Google, e travar por e-mail só criaria um jeito fácil de negar serviço à conta alheia.

---

## 9. Como testar localmente

Sem credencial do Google (o padrão) o endpoint responde 503 — é o teste de que nada quebrou:

```bash
curl -i -X POST localhost:8080/auth/social \
  -H 'Content-Type: application/json' \
  -d '{"provider":"GOOGLE","idToken":"qualquer-coisa"}'
# HTTP/1.1 503
```

Com credencial, o `id_token` precisa ser real (o backend valida a assinatura contra o Google) — ou seja, o teste ponta a ponta pede o app rodando. O roteiro está na seção 9 de `docs/api.http`.

Cobertura automatizada: `GoogleIdTokenVerifierTest` (audiência, claims, decoder falhando) e `SocialAuthServiceTest` (vinculação, cadastro, suspensão, provedor ausente).
