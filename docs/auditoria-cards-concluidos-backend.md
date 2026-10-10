# Auditoria dos cards marcados como concluídos — visão do backend

**Data:** 09/10/2026, reauditado no mesmo dia após o ciclo de correção.
**Base do código:** branch `feature/mvp-backend-gaps` (`bc83d16`).
**Escopo:** todo card do quadro *UniMove — Desenvolvimento do App* que está na lista **Concluídas** ou tem o check de concluído marcado em qualquer lista. São 32 cards, conferidos contra o código do backend; o Flutter não entrou na análise.

## Resumo

A primeira auditoria encontrou 1 card não implementado, 3 cards que prometiam algo que o backend não fazia e 11 com lacunas. **Todas as lacunas de código foram fechadas** e validadas com 422 testes automatizados e um smoke E2E de 39 checagens contra Postgres 16 real (§6). Ficam pendentes só **ações operacionais** que dependem de acesso ao ambiente de produção (§5).

| Situação inicial | Qtde | Situação agora |
|---|---|---|
| ❌ Não implementado | 1 | ✅ implementado |
| ⚠️ Card promete algo que o backend não faz | 3 | ✅ implementado |
| 🟡 Implementado com lacunas | 11 | ✅ lacunas fechadas (pendências só operacionais em §5) |
| ✅ Sem ressalva | 17 | ✅ |

---

## 1. Antes ❌ não implementado

### [NOTIFICAÇÕES][P2] Campanhas administrativas — não homologadas
- [x] Campanhas administrativas com caixa de entrada in-app (sem push, que está fora do MVP por decisão): `GET /admin/campaigns/audience` (prévia do público), `POST /admin/campaigns` e `GET /admin/campaigns` (histórico com destinatários, status `SENT`/`NO_AUDIENCE` e quem disparou).
- [x] Segmentação por cidade e papel, entregue **só a contas ativas com opt-in promocional**; o envio fica auditado (`CAMPAIGN_SENT`).
- [x] App: `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read` (notificação de outro usuário → 404).
- [x] A exclusão da conta apaga a caixa de entrada.
- Fora do escopo, conforme o próprio card (item 3): integração com provedor de push.

## 2. Antes ⚠️ o card prometia algo que o backend não fazia

### [PERFIL] Editar dados do usuário
- [x] `PUT /users/me/phone`: troca de telefone pelo próprio usuário, com um **novo desafio do WhatsApp** para o número novo. A posse continua provada e o admin não precisa intervir. Número de outra conta → 409.

### [ADMIN] Paginação completa das listas
- [x] `GET /admin/drivers/pending` agora é paginado e traz `documentationStatus`.
- `GET /admin/cities` e `GET /admin/pricing` continuam sem paginação de propósito: são listas pequenas por natureza (cidades atendidas e combinações cidade × categoria).

### [RF02/RF10][P0] Aprovar ou reprovar documentos com justificativa
- [x] `GET /admin/drivers/documents/pending`: fila de análise paginada, com os mais antigos primeiro.
- [x] `documentationStatus` em cada motorista pendente.

## 3. Antes 🟡 implementado com lacunas

| Card | Lacuna | Resolução |
|---|---|---|
| [AUTH] Troca de senha e atualização de token | Trocar a senha não derrubava outras sessões | [x] `tokens_valid_after` (V29). O filtro JWT recusa token anterior (401), derruba os streams SSE e `PUT /users/me/password` devolve um token novo |
| [ADMIN] Gestão de motoristas e usuários | Senha temporária não obrigava troca | [x] `must_change_password`: o login devolve `mustChangePassword` e o filtro só libera a troca de senha e o perfil. O admin do seed já nasce obrigado a trocar |
|  | Não havia busca de usuários | [x] `GET /admin/users?q=&role=&status=&cidade=` |
|  | Não havia troca de telefone | [x] self-service via WhatsApp (§2) |
| [ADMIN] Métricas, preços e corridas | `/admin/rides` sem filtros nem detalhe | [x] filtros `status`, `cidade`, `from`, `to`, `paymentStatus` e `GET /admin/rides/{id}` |
|  | Corrida presa sem saída | [x] `POST /admin/rides/{id}/cancel` (motivo obrigatório, auditado, `cancelled_by=ADMIN`, V30) |
|  | `revenue.gross` ambíguo | [x] documentado como valor **cobrado**; o recebido fica em `payments.cashConfirmed` |
| [MOTORISTA] Histórico, ganhos brutos e avaliação | Ganho bruto misturava o que não entrou | [x] `/drivers/me/earnings` ganha `payments` (dinheiro recebido, pendente, divergente e Pix simulado) |
| [CORRIDA] Início, conclusão e cancelamento | `IN_PROGRESS` sem cancelamento | [x] motorista cancela com motivo e sem taxa; passageiro recebe 409 explicativo; admin cancela qualquer corrida não terminal |
| [CORRIDAS][P1] Validar histórico | Histórico sem endereço | [x] `origemEndereco`/`destinoEndereco` no item |
| [PASSAGEIRO][P0] Validar solicitação | Estimativa não travava o preço | [x] `POST /rides` aceita `expectedPrice`; preço diferente → 409 com o valor novo |
| [RNF04][P0] Segurança | SSE de conta suspensa seguia aberto | [x] `AccountAccessRevokedEvent` + `UserStreamRegistry` encerram os streams após o commit (suspensão, exclusão, troca e reset de senha) |
|  | IP do rate limit forjável | [x] primeiro IP **público** lido da direita do `X-Forwarded-For` (pula os proxies internos) |
|  | Senha do admin do seed | [x] obrigado a trocar no 1º login (V29) e alerta no boot |
| [RNF05][P0] Privacidade e LGPD | Sem expurgo por retenção | [x] job diário: corridas com mais de 5 anos perdem endereço, rota, paradas, posição e textos (coordenadas a ~1 km); comentários e mensagens antigas apagados; arquivos de documento reprovados ou substituídos há mais de 90 dias saem do storage |
|  | Leitura de CPF/CNH pelo admin não auditada | [x] `DRIVER_DOCUMENTATION_VIEWED` |
|  | Contas excluídas nas métricas | [x] fora das contagens |
| [RF02][P0] CPF/CNH e envio de documentos | CPF/CNH em texto claro | [x] AES-256-GCM em coluna + fingerprint HMAC para unicidade (V31); `DATA_ENCRYPTION_KEY` obrigatória em prod |
|  | Sem dígito verificador da CNH | [x] `cnhCheckDigitValid` como **sinal** para o admin. É sinal e não bloqueio porque há variantes do algoritmo e um falso negativo barraria motorista legítimo |
|  | Sem antivírus | [x] a imagem é **decodificada e regravada** (some EXIF/GPS e carga poliglota; corrompida → 400) e PDF com JavaScript/Launch/anexo/XFA → 400. Limitação registrada: nome dentro de object stream comprimido não é inspecionado |
| — | (achado na revalidação) CNH vencida permitia duplicar um envio em análise | [x] corrigido |

## 4. Cards já corretos na primeira auditoria

[AUTH] Login e sessão · [ÉPICO][AUTH] · [INFRA] Base API · [CORRIDA] Estimativa, categorias, paradas · [CORRIDA] Aceite com GPS · [CORRIDA] Avaliação e histórico · [REALTIME] SSE + polling · [CHAT] · [MOTORISTA] Online/offline · [MAPAS] · [LOCAIS] · [ÉPICO][RF10] · [MOTORISTA][P0] Validar aceite · [ÉPICO][RF02] · [RF09] Dinheiro · [PERFIL][P1] Preferências · [RF04][P1] GPS.

## 5. Pendente — só operação (fora do código)

- [ ] **Criar o bucket privado `driver-documents`** no Supabase de produção e configurar `SUPABASE_URL`/`SUPABASE_SERVICE_KEY` no Render. Até lá o upload responde 503.
- [ ] **Configurar `DATA_ENCRYPTION_KEY`** no Render (`openssl rand -base64 32`) **antes** do deploy: sem ela a API não sobe em prod. Guardar a chave em local seguro, porque perdê-la torna CPF/CNH ilegíveis.
- [ ] Após o deploy, logar como admin e trocar a senha do seed (o sistema já obriga).
- [ ] Rodar as consultas de pré-checagem do `docs/homologacao-runbook.md` §2 antes de aplicar a V23.

## 6. Como foi validado

- **Testes:** 422 automatizados, 0 falhas.
- **Smoke E2E contra Postgres 16 real**, com a API subindo do zero (migrations V1–V32 aplicadas, Hibernate `validate`, queries JPQL e nativas aceitas). Foram **39/39 checagens**:
  - senha temporária e revogação de sessão;
  - CPF cifrado no banco e unicidade pelo hash;
  - fila de documentos e auditoria de leitura;
  - busca de usuários;
  - preço travado;
  - cancelamentos do motorista e do admin;
  - filtros e detalhe de corridas;
  - extrato por pagamento e histórico com endereço;
  - troca de telefone;
  - campanhas e caixa de entrada;
  - **SSE derrubado na suspensão**;
  - **retenção real** (corrida de 6 anos anonimizada, recente intacta);
  - exclusão limpando a caixa;
  - rate limit resistente a `X-Forwarded-For` forjado.
- **Log:** sem nenhum 500 nem ERROR inesperado.
- **Bugs que só a validação real pegou, ambos corrigidos com teste de regressão:**
  - o boot falhava pelo construtor duplicado da `FieldCrypto`;
  - aparecia um ERROR no fim do SSE de conta revogada.
