# Auditoria dos cards marcados como concluídos — visão do backend

**Data:** 09/10/2026
**Base do código:** branch `feature/mvp-backend-gaps` (`6514f35`), que inclui tudo da `main` e as entregas desta sessão.
**Escopo:** todo card do quadro *UniMove — Desenvolvimento do App* que está na lista **Concluídas** ou tem o check de concluído marcado em qualquer lista. São 32 cards. Cada um foi conferido contra o código do backend; o Flutter não entrou na análise.

## Resumo

| Situação | Qtde | Cards |
|---|---|---|
| ❌ **Não implementado no backend** | 1 | [NOTIFICAÇÕES][P2] Campanhas administrativas |
| ⚠️ **Card promete algo que o backend não faz** | 3 | [PERFIL] Editar dados do usuário · [ADMIN] Paginação completa das listas · [RF02/RF10] Aprovar ou reprovar documentos |
| 🟡 **Implementado, com lacunas a registrar** | 11 | listados em §3 |
| ✅ **Implementado sem ressalva relevante** | 17 | listados em §4 |

---

## 1. ❌ Não implementado

### [NOTIFICAÇÕES][P2] Campanhas administrativas — não homologadas
*Lista: Preparada, com o check de concluído marcado.*

O próprio card diz "NÃO DECLARAR ENTREGUE" e mesmo assim está marcado. No backend **não existe nada** de campanhas: nem templates, segmentação, fila, histórico de disparos, endpoint de admin ou integração com provedor de push. O único item relacionado é o opt-in promocional (`PUT /users/me/preferences`), que é pré-requisito e não a entrega.

- [ ] **Ação:** desmarcar o check. O card é V2/P2 condicional.

---

## 2. ⚠️ Card promete algo que o backend não faz

### [PERFIL] Editar dados do usuário
O card lista **"Alterar telefone"**. O backend **não permite** isso, por decisão de segurança: o telefone é provado pelo WhatsApp no cadastro, e deixá-lo editável anularia a verificação. O CLAUDE.md diz que a "troca de número passa pelo admin", mas **não existe endpoint de admin para trocar telefone**. Hoje a troca só é possível mexendo direto no banco.

- [ ] Corrigir o texto do card (nome e cidade são editáveis; telefone não).
- [ ] Decidir o fluxo de troca de número. Sugestão: novo desafio de WhatsApp autenticado (`POST /users/me/phone`), que mantém a prova de posse e dispensa o admin.

### [ADMIN] Paginação completa das listas
- `GET /admin/drivers/pending` devolve **lista sem paginação** (`List<PendingDriverItem>`). As outras listas de admin (`/admin/rides`, `/admin/users/suspended`, `/admin/audit`) são paginadas.
- `GET /admin/cities` e `GET /admin/pricing` também não paginam, mas são listas pequenas por natureza, então é aceitável.
- [ ] Paginar `/admin/drivers/pending`.

### [RF02/RF10][P0] Aprovar ou reprovar documentos com justificativa
O item 2 do card pede um **"painel de documentos pendentes"**. Hoje o admin aprova e reprova documento por motorista (`/admin/drivers/{id}/documentation`), mas **não há endpoint que liste os documentos aguardando análise** de todos os motoristas. O índice `idx_driver_documents_pending` (V27) existe, mas nenhuma query o usa. Na prática o admin precisa abrir motorista por motorista a partir de `/admin/drivers/pending`, que não mostra a situação documental.

- [ ] `GET /admin/drivers/documents/pending` (paginado): documentos `SUBMITTED` mais antigos primeiro, com motorista e tipo.
- [ ] Incluir `documentationStatus` em `GET /admin/drivers/pending`.

---

## 3. 🟡 Implementado, com lacunas

### [AUTH] Troca de senha e atualização de token
- [ ] **Trocar a senha não derruba outras sessões.** O JWT é stateless e vale 24 h: um token roubado continua válido depois da troca. O mesmo vale para o reset pelo admin. Correção barata: coluna `password_changed_at` e o filtro JWT recusando token com `iat` anterior a ela, aproveitando a consulta que já faz por request.

### [ADMIN] Gestão de motoristas e usuários
- [ ] **A senha temporária do reset não obriga troca** no próximo login (falta um campo `mustChangePassword`). Ela é ditada por telefone ou WhatsApp e pode ficar valendo indefinidamente.
- [ ] Não existe **busca ou listagem geral de usuários** para o admin. Só há a lista de suspensos e a de motoristas pendentes. Para suspender alguém, o admin precisa já saber o UUID.
- [ ] Não existe endpoint de admin para **trocar telefone** (ver §2).

### [ADMIN] Métricas, preços e corridas
- [ ] `GET /admin/rides` só filtra por `paymentStatus`. Faltam filtros por **status, cidade e período**, e não há **detalhe de uma corrida** para o admin (`GET /admin/rides/{id}`), útil para investigar disputa.
- [ ] Não existe **cancelamento administrativo** de corrida. Uma corrida presa em `IN_PROGRESS` (motorista some) só sai pelo banco.
- [ ] `revenue.gross` em `/admin/metrics` ainda soma corrida Pix **simulada** como receita. O bloco novo `payments` separa o que foi recebido, mas o nome `gross` engana.

### [MOTORISTA] Histórico, ganhos brutos e avaliação
- [ ] `GET /drivers/me/earnings` soma o `preco` de **toda corrida concluída**, inclusive Pix simulado e dinheiro `DIVERGENT` ou `PENDING`. "Ganho bruto" fica acima do que o motorista recebeu. Falta separar por `paymentStatus` (confirmado × pendente × divergente).

### [CORRIDA] Início, conclusão e cancelamento
- [ ] Corrida em `IN_PROGRESS` **não pode ser cancelada por ninguém**: a máquina de estados só permite `COMPLETED`. Em emergência (pane, passageiro desiste no meio), o motorista é obrigado a "concluir". Decidir a regra: cancelamento em andamento com motivo, ou cancelamento pelo admin.

### [CORRIDAS][P1] Validar histórico; recibo depende de pagamento
- [ ] `RideHistoryItem` **não traz `origemEndereco`/`destinoEndereco`** (só coordenadas), embora a corrida tenha esses campos. A tela de histórico fica sem endereço legível ou precisa chamar o reverse geocoding para cada item.
- Recibo: fora do escopo, como o próprio card registra.

### [PASSAGEIRO][P0] Validar solicitação e estimativa existentes
- [ ] **A estimativa não trava o preço.** `POST /rides` recalcula a rota e o surge. Se o surge mudar entre a tela de estimativa e o pedido, o preço salvo difere do exibido sem aviso. Opções: devolver o `preco` no `POST /rides` e exigir confirmação quando divergir, ou um `quoteId` com validade curta.

### [RNF04][P0] Segurança: remover demo de produção e proteger acesso
- [ ] Stream SSE já aberto de conta suspensa continua até expirar (30 min). Os requests novos já são barrados.
- [ ] O rate limit usa o 1º IP do `X-Forwarded-For`, que o cliente pode falsificar. Usar o IP adicionado pelo proxy do Render.
- [ ] A rotação da senha do admin do seed é operacional e ainda não foi feita (o boot alerta).

### [RNF05][P0] Privacidade, consentimento e LGPD
- [ ] Não há **rotina de expurgo por retenção**: corridas e documentos reprovados ficam para sempre. Depende do prazo jurídico (ver `docs/lgpd-inventario-dados.md` §3).
- [ ] O acesso de admin a **dado pessoal fora dos documentos** (ex.: `GET /admin/drivers/{id}/documentation`, que mostra CPF e CNH) não é auditado; só a URL do arquivo é.
- [ ] Contas `DELETED` continuam entrando nas contagens de usuários do `/admin/metrics`.

### [RF02][P0] Cadastro de CPF, CNH e veículo completo / [RF02][P0] Envio privado de CNH, CRLV e selfie
- [ ] O número da CNH só é validado no formato (11 dígitos), **sem dígito verificador**.
- [ ] CPF e CNH ficam **em texto claro** no banco. É aceitável no MVP com acesso restrito, mas vale avaliar criptografia em coluna.
- [ ] Os arquivos são checados por *magic bytes*, sem **antivírus**.
- [ ] O bucket privado **ainda não foi criado** no Supabase de produção. Até lá o upload responde 503.

---

## 4. ✅ Implementados sem ressalva relevante (backend)

| Card | Evidência |
|---|---|
| [AUTH] Login e sessão implementados — cadastro real pendente | `/auth/login`, JWT, lockout; o cadastro exige `verificationToken` e cidade de `/cities` |
| [ÉPICO][AUTH] Autenticação: pendências e integração (não duplicar) | login, cadastro, social, troca e reset de senha. A recuperação self-service é outro card ([RF01][P1], aberto) |
| [INFRA] Base API, PostgreSQL, SSE e mapas existente | V1–V28 aplicadas num Postgres real, `validate` ok. Ressalva de operação: o Render free hiberna e para os `@Scheduled` |
| [CORRIDA] Estimativa, categorias, paradas e seleção de método | `/rides/estimate` com opções por categoria, até 5 paradas, `confirm-payment` |
| [CORRIDA] Aceite com posição GPS real | `AcceptRideRequest {lat,lng}`, ETA de chegada |
| [CORRIDA] Avaliação e histórico paginado | `/rides/{id}/rating` único por participante; `/rides/history` paginado e filtrável |
| [REALTIME] SSE + sincronização periódica | três hubs SSE com snapshot na reconexão e heartbeat de 15 s; `GET /rides/{id}` para polling. Escala só em instância única (RNF08) |
| [CHAT] Chat durante corrida | só participantes, só em `DRIVER_EN_ROUTE`/`IN_PROGRESS`, `Last-Event-ID` |
| [MOTORISTA] Online/offline e corridas disponíveis | online exige aprovação e CNH válida; mural por cidade e categoria; auto-offline |
| [MAPAS] Busca e geocodificação reversa | `/maps/geocode` e `/maps/reverse` (Photon) com cache e rate limit |
| [LOCAIS] Destinos recentes e locais salvos | `/rides/recent-destinations`, `/saved-places` |
| [ÉPICO][RF10][P1] Administração: cidades, documentos e auditoria | admin de cidades, revisão documental, `admin_audit_log` |
| [MOTORISTA][P0] Validar aceite, cancelamento e GPS existente | `@Version` + índice único de motorista ativo; cancelamento com motivo; GPS validado |
| [ÉPICO][RF02][P0] Cadastro documental do motorista — coordenação | ressalvas nos cards filhos (§2 e §3) |
| [RF09][P0] Pagamento em dinheiro com registro operacional | `changeFor`, `cash-payment`, `DIVERGENT`, filtro de conciliação. Ressalva no extrato do motorista (§3) |
| [PERFIL][P1] Preferências e ajuda — escopo isolado | `/users/me/preferences`, exclusão de conta. "Ajuda" é UI |
| [RF04][P1] GPS durante corrida/background: validar e complementar | timestamp, precisão, posição velha, salto e fora de ordem; limpeza ao encerrar. O envio em background é do app |

> Para as verificações, ver `docs/homologacao-runbook.md` §7: smoke E2E contra Postgres real e 368 testes automatizados.

---

## 5. Prioridade sugerida para fechar as lacunas

1. **P0 / piloto:** desmarcar [NOTIFICAÇÕES]; derrubar sessões na troca ou reset de senha e forçar troca da temporária; cancelamento de corrida em andamento (motorista com motivo, ou admin); criar o bucket de documentos.
2. **P1:** fila de documentos pendentes e paginação de `/admin/drivers/pending`; ganhos do motorista por `paymentStatus`; endereços no histórico; filtros e detalhe em `/admin/rides`; busca de usuários no admin.
3. **P2:** troca de telefone por novo desafio; preço travado pela estimativa; dígito verificador da CNH; expurgo por retenção; IP confiável no rate limit.
