# UniMove — Plano de Produção do Backend

> **Objetivo:** levar o backend monolítico do MVP de "funciona na minha máquina" para **produção real**, pronto para atender o piloto de Remanso/BA.
> Organizado em **fases** e **subfases**, com critérios de aceite por bloco. Cada subfase é uma unidade de trabalho que vira (idealmente) uma branch + PR.
>
> Escopo: **apenas o backend**. App Flutter, jurídico e marketing ficam fora deste documento.
> Premissas de stack já fechadas: **Railway** (hospedagem) · **Google Maps** (Routes + Geocoding) · **Mercado Pago com split** (Pix) · **Cloudflare R2 / bucket** (storage de documentos) · CNPJ existente.

---

## Visão geral das fases

| Fase | Nome | Foco | Pré-requisito |
|---|---|---|---|
| **0** | Fundação & higiene | Travar versões, segredos, perfis, baseline de testes | — |
| **1** | Hardening de segurança | JWT, CORS, rate limit, headers, validação | Fase 0 |
| **2** | Integrações de produção | Google Maps, Mercado Pago Pix + split + webhook, storage | Fase 1 |
| **3** | Observabilidade & resiliência | Logs, métricas, health, erros, timeouts/retry | Fase 2 (paralelizável c/ 2) |
| **4** | Empacotamento & deploy | Dockerfile prod, Railway, migrations, CI/CD | Fases 1–3 |
| **5** | Dados & configuração da cidade | Pricing Remanso, seeds, backup, LGPD | Fase 4 |
| **6** | Pré-lançamento | Carga, smoke E2E, runbook, go-live | Fases 0–5 |

> Caminho crítico: **0 → 1 → 2 → 4 → 6**. A Fase 3 corre em paralelo. A Fase 5 depende do deploy estar de pé.

---

## Fase 0 — Fundação & higiene

Garante que o projeto é reprodutível e seguro de evoluir antes de mexer em features.

### 0.1 Travar build e versões
- Fixar versão exata de Spring Boot, Java 21 e plugins no `pom.xml` (sem ranges).
- Garantir `mvn -DskipTests package` reprodutível.
- **Aceite:** build limpo em máquina nova só com JDK 21 + Maven.

### 0.2 Gestão de segredos
- Remover qualquer segredo do versionado (revisar `application.yml`, `.env`, histórico).
- Padronizar **todas** as credenciais via env (`JWT_SECRET`, `DATABASE_*`, chaves Google/Mercado Pago, R2) — nada hardcoded.
- `.env.example` atualizado com **todas** as variáveis novas (sem valores reais).
- **Aceite:** app sobe em prod lendo 100% dos segredos do ambiente; nenhum segredo no git.

### 0.3 Perfis Spring (`dev` / `prod`)
- `application-prod.yml` com `ddl-auto=validate`, logs em nível adequado, pool de conexões dimensionado, Flyway habilitado.
- Confirmar que `SPRING_PROFILES_ACTIVE=prod` é o usado no Railway.
- **Aceite:** `prod` sobe sem depender de defaults de dev.

### 0.4 Baseline de testes verde
- `mvn test` passando (Postgres via Testcontainers). *Obs.: na máquina do user o Testcontainers quebra por bug do Docker Desktop 29.x — rodar a suíte no CI, não localmente.*
- Cobrir a **máquina de estados da Ride** e a **fórmula de preço** com testes (são o núcleo).
- **Aceite:** suíte verde no CI.

---

## Fase 1 — Hardening de segurança

### 1.1 JWT e autenticação
- `JWT_SECRET` ≥ 256 bits obrigatório (app falha ao subir se ausente/fraco).
- Revisar expiração, claims e validação de assinatura.
- **Aceite:** token forjado/expirado → 401; rotas protegidas exigem role correta.

### 1.2 CORS & headers de segurança
- CORS restrito às origens do app (sem `*` em prod).
- Headers: HSTS, `X-Content-Type-Options`, `X-Frame-Options`, etc.
- **Aceite:** origem não permitida bloqueada; headers presentes na resposta.

### 1.3 Rate limiting & anti-abuso
- Limitar `/auth/login`, `/auth/register` e endpoints de criação de corrida (proteção contra brute force / flood).
- **Aceite:** excesso de tentativas → 429.

### 1.4 Validação e tratamento de erros
- `@Valid` em todos os `@RequestBody`; `GlobalExceptionHandler` cobrindo 400/401/403/404/409/429/500 com payload consistente (sem stacktrace vazando).
- **Aceite:** erros retornam JSON padronizado, sem detalhes internos.

### 1.5 Autorização por recurso
- Revisar que dono/aceitante são checados em `/rides/{id}/*`, chat e rating (não só a role).
- **Aceite:** usuário A não acessa corrida de B.

---

## Fase 2 — Integrações de produção

Substitui os servidores demo (OSRM/Photon) e a simulação de pagamento por integrações reais.

### 2.1 Google Maps (Routes + Geocoding)
- Nova implementação de `MapsService` usando Google (Routes para distância/tempo/geometria; Geocoding/Places para autocomplete e reverse).
- **Manter o cache existente** (rota/geocode por coordenada arredondada) — é o que segura o custo.
- Chave em env; restrição de chave por API + faturamento configurado.
- Fallback/degradação se o Maps falhar (timeout + log ERROR, conforme CLAUDE.md).
- **Aceite:** `/maps/geocode`, `/maps/reverse`, `/rides/estimate` e `/rides/{id}/route` respondem via Google; cache reduz chamadas repetidas.

### 2.2 Mercado Pago — Pix dinâmico + split
- Implementação real de `PaymentService` (substituir `SimulatedPaymentService`): criar cobrança Pix QR dinâmico com **split** para a plataforma.
- Mapear estados de pagamento → transição `PENDING_PAYMENT` → `AVAILABLE_IN_MURAL`.
- **Aceite:** corrida só vai ao mural após pagamento confirmado.

### 2.3 Webhook de pagamento
- Endpoint público para callbacks do Mercado Pago, com **validação de assinatura** e **idempotência** (evento repetido não duplica efeito).
- **Aceite:** webhook confirma pagamento de forma idempotente; eventos forjados rejeitados.

### 2.4 Upload e aprovação de documentos do motorista
- Storage de objetos (Cloudflare R2/bucket): upload de CNH/documentos via URL pré-assinada.
- Fluxo de aprovação manual pelo ADMIN (estende `/admin/drivers/*`).
- **Aceite:** motorista envia documento; ADMIN aprova; só aprovado fica elegível a aceitar corridas.

### 2.5 Realtime (SSE) sob carga
- Validar SSE (`status-stream`, `chat/stream`) atrás do proxy do Railway (timeouts, keep-alive, reconexão).
- Documentar fallback por **polling** para o app (rede 4G fraca no interior).
- **Aceite:** stream sobrevive a reconexões; polling documentado como plano B.

---

## Fase 3 — Observabilidade & resiliência *(paralela à Fase 2)*

### 3.1 Logging estruturado
- SLF4J com correlação por request; níveis conforme CLAUDE.md (INFO transições de Ride, WARN conflito de mural, ERROR falha de gateway).
- **Aceite:** rastrear uma corrida ponta a ponta pelos logs.

### 3.2 Health checks & readiness
- Spring Boot Actuator: `/health` (liveness + readiness incluindo DB) para o Railway.
- **Aceite:** Railway usa o health check para gating de deploy.

### 3.3 Monitoramento de erros & uptime
- Sentry (ou similar) para exceções; monitor de uptime externo.
- **Aceite:** exceção em prod gera alerta.

### 3.4 Timeouts, retry e circuit breaking
- Timeouts no WebClient (Google/Mercado Pago); retry com backoff onde fizer sentido; nunca segurar transação esperando rede externa (já refatorado — manter o padrão).
- **Aceite:** lentidão do gateway não derruba a aplicação nem trava conexões de banco.

---

## Fase 4 — Empacotamento & deploy

### 4.1 Dockerfile de produção
- Multi-stage (build Maven → runtime JRE 21 slim), usuário não-root, JVM tunada para o container.
- **Aceite:** imagem sobe localmente lendo só env.

### 4.2 Provisionamento no Railway
- Serviço do app + Postgres gerenciado + ambiente de **staging** separado.
- Variáveis de ambiente configuradas (prod e staging).
- **Aceite:** app acessível por URL HTTPS no Railway.

### 4.3 Migrations em produção
- Flyway roda no startup; estratégia para migrations grandes/lentas; `flyway:info` como checagem.
- **Aceite:** deploy aplica migrations sem intervenção manual.

### 4.4 CI/CD
- Pipeline (GitHub Actions): build + `mvn test` (Testcontainers no CI) + deploy para staging no merge; promoção manual para prod.
- **Aceite:** push na main → staging automático; prod sob aprovação.

### 4.5 Domínio & TLS
- Domínio `.com.br` apontando para o Railway; TLS automático.
- **Aceite:** API servida em domínio próprio com HTTPS.

---

## Fase 5 — Dados & configuração da cidade

### 5.1 Pricing de Remanso
- Migration de seed com `PricingConfig` para Remanso (MOTO e CARRO): base + por km + por min, além do `_DEFAULT`.
- **Aceite:** `/rides/estimate` retorna preço correto para Remanso nas duas categorias.

### 5.2 Seeds de produção
- Admin inicial com hash BCrypt real (não o de teste); revisar `V2__seed_admin.sql`.
- **Aceite:** login admin funciona em prod com credencial segura.

### 5.3 Backup & retenção
- Confirmar backup automático do Postgres (Railway) e testar **restore**.
- **Aceite:** restore validado em staging.

### 5.4 LGPD mínimo
- Inventário de dados pessoais (passageiro/motorista/documentos); política de retenção e rota de exclusão de conta.
- **Aceite:** é possível excluir/anonimizar um usuário a pedido.

---

## Fase 6 — Pré-lançamento

### 6.1 Teste de carga
- Simular o pico previsto (~150 corridas/dia + mural + SSE) contra staging; dimensionar o plano Railway.
- **Aceite:** latência e erro dentro do aceitável no pico projetado.

### 6.2 Smoke E2E
- Roteiro ponta a ponta: registro → login → estimate → criar corrida → pagar (Pix sandbox) → mural → aceitar → iniciar → completar → avaliar.
- **Aceite:** fluxo completo passa em staging com integrações reais (sandbox).

### 6.3 Runbook operacional
- Doc em `/docs`: como deployar, rollback, ler logs, girar segredos, responder a incidente de pagamento/mapas.
- **Aceite:** outra pessoa consegue operar seguindo o runbook.

### 6.4 Go-live
- Migrar integrações de sandbox → produção (Mercado Pago, Google billing), checklist final, primeiro deploy de produção monitorado.
- **Aceite:** corrida real de ponta a ponta em Remanso com pagamento real.

---

## Checklist resumido (go/no-go)

- [ ] Segredos 100% via env, nada no git
- [ ] `prod` sobe com `ddl-auto=validate` + Flyway
- [ ] JWT forte, CORS restrito, rate limit, erros padronizados
- [ ] Google Maps em produção com cache
- [ ] Pix Mercado Pago + split + webhook idempotente
- [ ] Upload + aprovação de documentos
- [ ] Health check, logs estruturados, alertas de erro
- [ ] Dockerfile prod + Railway (prod + staging) + CI/CD
- [ ] Pricing de Remanso + admin seguro + backup testado
- [ ] Carga, smoke E2E e runbook prontos

---

## Sequenciamento sugerido (ordem de execução)

1. **Fase 0** inteira (fundação).
2. **Fase 1** (segurança) — barata e destrava confiança.
3. **Fase 4.1–4.2** cedo (Dockerfile + Railway/staging) para ter ambiente real onde testar integrações.
4. **Fase 2** (integrações) validando direto em staging.
5. **Fase 3** em paralelo com a 2.
6. **Fase 4.3–4.5** (migrations, CI/CD, domínio).
7. **Fase 5** (dados/cidade).
8. **Fase 6** (pré-lançamento e go-live).

> Cada subfase = 1 branch a partir da `main` atualizada → commits → PR (o Codex revisa). Recomendo PRs pequenos por subfase em vez de um PR gigante por fase.
