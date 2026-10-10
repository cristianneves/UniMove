# LGPD — inventário de dados e direitos do titular

Card: **[RNF05][P0] Privacidade, consentimento e LGPD**. Este documento é a base técnica. O texto da política de privacidade publicado (`PRIVACY_POLICY_URL`) precisa ser escrito ou revisado por quem responde juridicamente pela UniMove.

## 1. Inventário

| Dado | Quem | Finalidade | Base legal (sugerida) | Compartilhado com | Retenção |
|---|---|---|---|---|---|
| Nome, e-mail, senha (hash BCrypt) | todos | conta e login | execução de contrato | — | até a exclusão da conta |
| Telefone (`wa_id` verificado) | todos | prova de posse do número, contato na corrida | execução de contrato | Meta (recebemos a mensagem; nada é enviado) | até a exclusão |
| Cidade | todos | escopo de operação (mural, preço) | execução de contrato | — | mantida anonimizada (métrica) |
| Vínculo Google (`sub`, e-mail) | quem usa login social | login sem senha | execução de contrato | Google (validação do ID Token) | até a exclusão |
| CPF, CNH (número, categoria, validade), veículo | motorista | habilitação para operar e segurança do passageiro | obrigação legal / legítimo interesse (segurança) | — | até a exclusão. **CPF e número da CNH cifrados (AES-256-GCM)** |
| Arquivos CNH, CRLV e selfie | motorista | conferência documental | idem | Supabase (storage privado, operador) | até a exclusão; reprovados ou substituídos: **90 dias**. Fotos regravadas sem EXIF/GPS |
| Origem, destino, paradas, endereços | passageiro | executar a corrida e calcular o preço | execução de contrato | motorista da corrida; OSRM/Photon (só coordenadas) | **5 anos**; depois anonimizados por job diário (sem endereço, rota ou paradas; coordenadas a ~1 km) |
| Posição do motorista | motorista | acompanhamento da corrida pelo passageiro e pelo link compartilhado | execução de contrato / segurança | passageiro da corrida; quem tem o link | **apagada ao concluir ou cancelar a corrida** |
| Mensagens de chat | participantes | comunicação na corrida | execução de contrato | outro participante | até a exclusão de quem enviou ou 5 anos |
| Caixa de entrada (notificações) | todos | avisos e campanhas (com opt-in) | consentimento (promocional) / execução de contrato | — | até a exclusão |
| Avaliações (nota e comentário) | participantes | qualidade e segurança | legítimo interesse | — | nota retida (compõe a média do outro); comentário apagado na exclusão |
| Pagamento em dinheiro (valor recebido, observação) | motorista | conciliação | execução de contrato / obrigação legal | — | retido com a corrida |
| Consentimento (versão e data do aceite) | todos | prova do consentimento | obrigação legal | — | mantido |
| Opt-in promocional | todos | marketing | **consentimento** | — | até revogar |
| IP (rate limit em memória; log de acesso ao `/share`) | todos | segurança | legítimo interesse | — | memória: minutos; log: retenção da plataforma |

## 2. Direitos do titular, já implementados

| Direito (art. 18) | Como |
|---|---|
| Confirmação e acesso | `GET /users/me`, `GET /users/me/data-export` |
| Portabilidade | `GET /users/me/data-export` (JSON). Os arquivos de documento são entregues pelo suporte, a pedido |
| Correção | `PUT /users/me` (nome e cidade); `PUT /drivers/me/documentation/profile` enquanto o cadastro não está aprovado. O telefone muda via admin |
| Eliminação | `DELETE /users/me`: anonimização imediata (§3) |
| Revogação do consentimento | `PUT /users/me/preferences {"marketingOptIn": false}`; para os termos, a exclusão da conta |
| Informação sobre compartilhamento | este documento e a política publicada |

## 3. Exclusão de conta (`DELETE /users/me`)

- Exige a senha quando a conta tem senha (um token vazado não basta). Conta só-social envia o corpo vazio.
- Bloqueada (409) com corrida em andamento.
- **Apaga na hora:** nome, e-mail, telefone, senha, vínculo Google, locais salvos, mensagens enviadas, comentários de avaliação, opt-in, CPF/CNH/veículo e os **arquivos** de documento no storage.
- **Retém, sem identificação:** a linha do usuário (status `DELETED`, nome "Usuário removido") e as corridas, porque outros titulares e a conciliação dependem delas (defesa em disputa, obrigação fiscal). O token atual para de valer (401) e o telefone fica livre para um novo cadastro.
- **Retenção implementada:** corridas com mais de 5 anos são anonimizadas por um job diário (`RETENTION_RIDE_YEARS`). Validar o prazo com o jurídico; ele é configurável sem deploy.

## 4. Controles de acesso

- Documentos do motorista: bucket privado, sem URL pública. O admin vê por URL assinada de 5 minutos e **cada emissão fica em `admin_audit_log`** (`DRIVER_DOCUMENT_VIEWED`).
- Ações administrativas (aprovar, reprovar, suspender, resetar senha, mudar preço ou cidade) ficam auditadas em `GET /admin/audit`.
- O link compartilhado (`/share/{token}`) expira quando a corrida termina, e cada acesso é logado com o prefixo do token e o IP.
- A posição do motorista só é aceita em `DRIVER_EN_ROUTE`/`IN_PROGRESS` e é apagada ao encerrar.
- Conta suspensa ou excluída perde o acesso imediatamente (filtro JWT).

## 5. Pendências fora do backend

- [ ] Texto da política de privacidade e dos termos publicado em `PRIVACY_POLICY_URL` (jurídico/produto).
- [ ] Avisos no app: link da política no cadastro (`GET /legal/terms`) e explicação antes de pedir permissão de GPS (Flutter).
- [ ] Definir o encarregado (DPO) e o canal de contato do titular.
- [x] Rotina de retenção (5 anos para corridas, 90 dias para arquivos obsoletos), configurável. Falta só a validação jurídica do prazo.
