# Login com Google — guia para o app (Flutter)

Guia de integração do app com o login social do backend UniMove.

> **Status:** o backend está pronto e mergeado. O projeto no Google Cloud, a tela de consentimento e o **client ID Web** já foram criados — **o client ID Web foi enviado a você pelo WhatsApp**. Falta o que está descrito aqui.
>
> O client ID **não é segredo** (ele fica embutido no app, qualquer pessoa consegue extrair), então tudo bem tê-lo recebido por WhatsApp. O que nunca circula é *client secret* — e este fluxo não usa nenhum.

---

## O que falta fazer

1. Criar os OAuth clients de **Android** e **iOS** no Google Cloud (§1)
2. Configurar o `google_sign_in` no projeto Flutter (§2)
3. Implementar os dois fluxos de tela (§3 e §4)
4. Tratar os erros (§6)

---

## 1. OAuth clients de plataforma

Estes clients **não vão para o backend** — eles só autorizam o app a pedir o token ao Google. O `aud` do `id_token` continua sendo o client ID **Web** que você recebeu.

### Você tem acesso ao Google Cloud Console?

**Se sim** — 🔗 https://console.cloud.google.com/apis/credentials, projeto **UniMove**, `Create credentials → OAuth client ID`:

| Application type | O que preencher |
|---|---|
| **Android** | package name (ex.: `com.unimove.app`) + SHA-1 |
| **iOS** | bundle ID |

**Se não** — mande os dados abaixo para o Cristian, que cria por você:

- package name do Android
- SHA-1 de **debug**
- SHA-1 de **release**
- bundle ID do iOS (se houver app iOS)

### Como obter o SHA-1

**Debug:**

```bash
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android
```

**Release:** aqui mora a pegadinha. Se o app usa **Google Play App Signing** (o padrão para apps novos na Play Store), o SHA-1 que vale **não é o do seu keystore local** — é o do *App signing key certificate*, em 🔗 https://play.google.com/console → seu app → `Test and release → Setup → App integrity`.

Registrar o SHA-1 errado produz o pior tipo de bug: o login funciona no seu emulador e falha para todo mundo em produção.

Cadastre **os dois** SHA-1 (debug e release) — no mesmo client Android ou em dois clients, tanto faz.

---

## 2. Configuração no projeto Flutter

### 2.1 Dependência

```yaml
dependencies:
  google_sign_in: ^<versão atual>
```

### 2.2 O ponto crítico: `serverClientId`

```dart
final googleSignIn = GoogleSignIn(
  // ↓ o client ID WEB que você recebeu no WhatsApp — NÃO o client ID Android
  serverClientId: '<CLIENT_ID_WEB>.apps.googleusercontent.com',
  scopes: ['email', 'profile'],
);

final account = await googleSignIn.signIn();
final idToken = (await account!.authentication).idToken;
```

Duas coisas que causam ~90% dos 401 nessa integração:

- **`serverClientId` deve ser o client ID Web.** Com o client ID Android ali, o Google emite um `id_token` com um `aud` que o backend recusa.
- **Mande `idToken`, nunca `accessToken`.** O access token não é assinado nem verificável offline; o backend não tem como validá-lo e recusa.

> A API do `google_sign_in` mudou entre as versões maiores (`signIn()` virou `initialize()`/`authenticate()` nas mais recentes). Confira a API da versão que você está usando — o que importa não muda: `serverClientId` = client ID **Web**, e o que sai é o `idToken`.

### 2.3 iOS

Além do client ID iOS no console, o `Info.plist` precisa de:

```xml
<key>GIDClientID</key>
<string>&lt;CLIENT_ID_IOS&gt;.apps.googleusercontent.com</string>

<key>CFBundleURLTypes</key>
<array>
  <dict>
    <key>CFBundleURLSchemes</key>
    <array>
      <!-- o client ID iOS INVERTIDO: com.googleusercontent.apps.<id> -->
      <string>com.googleusercontent.apps.&lt;CLIENT_ID_IOS&gt;</string>
    </array>
  </dict>
</array>
```

No Android não é preciso `google-services.json` para este fluxo.

---

## 3. Fluxo A — usuário que já tem conta

```
sign-in nativo do Google  →  idToken
POST /auth/social  {"provider":"GOOGLE","idToken":"..."}
```

Resposta **200**:

```json
{
  "status": "AUTHENTICATED",
  "auth": {
    "token": "eyJhbGciOi...",
    "userId": "64179e40-9bce-4e4e-92af-2dd6d1bf7d34",
    "role": "MOTORISTA",
    "cidade": "remanso",
    "expiresAt": "2026-08-05T20:16:24Z"
  },
  "profile": null
}
```

`auth.token` é o JWT do UniMove — o mesmo que o login por e-mail/senha devolve. Guarde e mande em `Authorization: Bearer <token>` daqui em diante. Nada muda no resto do app.

**Vinculação automática:** se a pessoa já tinha conta com e-mail e senha e o e-mail do Google é o mesmo, o backend vincula sozinho e devolve `AUTHENTICATED` já no primeiro toque. Ela continua podendo entrar pelos dois jeitos. Você não precisa fazer nada para isso acontecer.

---

## 4. Fluxo B — primeiro acesso

A mesma chamada do §3 pode devolver **200** com outro `status`:

```json
{
  "status": "REGISTRATION_REQUIRED",
  "auth": null,
  "profile": { "email": "maria@gmail.com", "name": "Maria Silva" }
}
```

Não é erro — é o caminho de cadastro. Use `profile` para pré-preencher a tela.

### Por que ainda tem WhatsApp no meio

O Google prova quem é dono do **e-mail**. Ele não prova quem é dono do **telefone** — e num app de corridas passageiro e motorista precisam de número real e verificado. Então o login social substitui a senha, nunca a verificação do telefone. O cadastro social passa **exatamente** pelo mesmo fluxo de WhatsApp que o cadastro por e-mail já usa hoje.

### 4.1 Criar o desafio

```
POST /auth/phone/challenge      (sem body, sem auth)
```

```json
{
  "challengeId": "c677087b-1894-4380-977f-95969675c5b5",
  "code": "3FUDYTR3",
  "waLink": "https://wa.me/55XXXXXXXXXXX?text=UNIMOVE-3FUDYTR3",
  "expiresAt": "2026-08-04T20:24:00Z"
}
```

Abra `waLink` no WhatsApp (a mensagem já vai pré-preenchida). Mostre o `code` na tela como alternativa, caso a pessoa precise digitar.

**429** aqui significa limite por IP — mostre "tente novamente em alguns minutos".

### 4.2 Polling do status

```
GET /auth/phone/challenge/{challengeId}      a cada ~2s
```

```json
{
  "status": "VERIFIED",
  "phone": "(74) 9****-0001",
  "verificationToken": "B4DTnFaXza0C...",
  "rejectionReason": null
}
```

| `status` | O que fazer |
|---|---|
| `PENDING` | continuar o polling |
| `VERIFIED` | guardar `verificationToken` e seguir para 4.3 |
| `REJECTED` | `rejectionReason: "PHONE_IN_USE"` → "este número já tem conta"; ofereça entrar |
| `EXPIRED` | o prazo acabou; voltar ao 4.1 |

O `phone` vem mascarado, só para a pessoa conferir na tela.

### 4.3 Completar o cadastro

```
POST /auth/social/register
```

```json
{
  "provider": "GOOGLE",
  "idToken": "<O MESMO idToken do passo 3>",
  "verificationToken": "<do passo 4.2>",
  "role": "PASSAGEIRO",
  "cidade": "Remanso",
  "vehicleType": null,
  "vehiclePlate": null
}
```

Para `role: "MOTORISTA"`, `vehicleType` (`MOTO` ou `CARRO`) e `vehiclePlate` são **obrigatórios**; para `PASSAGEIRO` devem ficar nulos. Mandar fora dessa regra dá 400.

Resposta **201** com o mesmo corpo de `auth` do §3 — já logado.

**Reenvie o mesmo `idToken`.** Ele é revalidado no backend, e é isso que evita inventarmos um "ticket de cadastro" com estado no servidor. Ele vale ~1h; se a pessoa demorar mais que isso no WhatsApp, refaça o sign-in do Google e use o token novo.

**Não existe campo `email` nem `password`.** O e-mail vem do token do Google e a conta nasce sem senha.

---

## 5. Conta sem senha

Quem entra por Google não tem senha. Consequências na UI:

- `GET /users/me` traz **`hasPassword: false`** → esconda a opção "trocar senha".
- Se ela tentar `PUT /users/me/password` mesmo assim, vem **409** com mensagem explicativa.
- Se ela tentar o login por e-mail/senha, vem **401 genérico** (o backend não revela que a conta é social — isso seria enumeração de usuário). Vale a pena o app oferecer "Entrar com Google" na tela de login para reduzir esse atrito.

Quem perder o acesso à conta Google resolve pelo suporte: o admin gera uma senha temporária e a conta passa a aceitar os dois métodos.

---

## 6. Erros

| HTTP | Significado | O que mostrar |
|---|---|---|
| **400** | payload inválido (falta `verificationToken`, motorista sem veículo, `role: ADMIN`) | erro de formulário; veja `fieldErrors` na resposta |
| **401** | `id_token` com assinatura, emissor, audiência ou validade inválidos | "não foi possível entrar com o Google"; refaça o sign-in |
| **403** | o e-mail da conta Google não está verificado | peça para verificar o e-mail no Google, ou usar outra conta |
| **409** | no `/auth/social/register`: a conta já existe | refaça o `POST /auth/social` — ele autentica (e vincula) |
| **429** | limite de desafios de telefone por IP | "tente de novo em alguns minutos" |
| **503** | login social não configurado no ambiente | esconda o botão do Google; é ambiente sem credencial |

Toda resposta de erro segue o mesmo formato:

```json
{
  "timestamp": "2026-08-04T20:14:20Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Não foi possível validar sua conta no provedor.",
  "path": "/auth/social"
}
```

Em 400 vem também `fieldErrors` — um mapa `campo → mensagem`, pronto para exibir no formulário.

### Debugando um 401 num login legítimo

Na ordem de probabilidade:

1. `serverClientId` está com o client ID **Android** em vez do **Web**
2. está sendo enviado o `accessToken` em vez do `idToken`
3. o client ID Web usado no app é diferente do configurado no backend
4. o SHA-1 de release não foi cadastrado (falha só no APK assinado)

---

## 7. Como testar

Enquanto o backend de dev estiver com o login social desligado, `POST /auth/social` responde **503** para qualquer payload. É o comportamento esperado — significa que o ambiente não tem credencial, não que o app está errado. Combine com o Cristian quando ligar (`GOOGLE_LOGIN_ENABLED=true`).

Um **401** com token falso já indica ambiente configurado:

```bash
curl -i -X POST <BASE_URL>/auth/social \
  -H 'Content-Type: application/json' \
  -d '{"provider":"GOOGLE","idToken":"invalido"}'
```

Enquanto isso, o cadastro por e-mail e senha continua funcionando normalmente e sem nenhuma mudança.

---

## 8. Referências

- Contrato completo e o porquê das decisões: [`docs/login-social-google.md`](./login-social-google.md)
- Fluxo do WhatsApp em detalhe: [`docs/verificacao-telefone.md`](./verificacao-telefone.md)
- Coleção HTTP (seções 0 e 1.5/1.6): [`docs/api.http`](./api.http)
- Swagger: `<BASE_URL>/swagger-ui.html`
