# UniMove — Plano de Piloto em Remanso/BA
## Custos de Infraestrutura & Ferramentas — Projeção de 6 meses

> **Documento para investidores.** Foco: demonstrar os **gastos de infraestrutura e ferramentas** ao longo de 6 meses, caso o UniMove entre em produção em Remanso/BA.
>
> A estimativa financeira deste documento considera **apenas infraestrutura e ferramentas**. Desenvolvimento, jurídico/contábil e marketing **não entram nesta conta** (são citados como contexto e serão orçados à parte). A projeção é **mediana**: custos realistas, sem espremer ao mínimo nem assumir tudo premium.
>
> Câmbio aproximado usado: US$ 1 ≈ R$ 5,40 · € 1 ≈ R$ 6,00 (jun/2026). Valores são estimativas, não cotações.

---

## 1. Sumário executivo

- **Oportunidade:** Remanso/BA, ~40.000 habitantes, interior (região do Lago de Sobradinho). **Cultura forte de mototáxi**, baixa oferta de transporte por aplicativo e uso de Pix já consolidado mesmo no interior.
- **Proposta:** piloto de 6 meses com **infraestrutura enxuta** (menor custo possível). O backend já existe; o app (Flutter) e as integrações de produção são o que falta.
- **Número-chave:** custo de **infraestrutura & ferramentas ≈ R$ 3.236 no semestre** (**~R$ 3.560** com margem de variação de uso de ~10%), média de **~R$ 540/mês**.
- **Transparência:** desenvolvimento, jurídico e marketing existem, porém são orçados separadamente — este documento isola o **custo recorrente de infra & ferramentas** para clareza.

---

## 2. Premissas operacionais (conservadoras)

| Premissa | M1–M3 (build) | M4 (lançamento) | M5 | M6 |
|---|---|---|---|---|
| Motoristas ativos | 0 (pré-operação) | ~12 | ~18 | ~25 |
| Corridas/dia | 0 | ~20 | ~80 | ~150 |
| Ticket médio (mototáxi curto) | — | R$ 8 | R$ 8 | R$ 8 |

O lançamento ao público ocorre em ~M4; os três primeiros meses são de construção e testes. Mototáxi (categoria MOTO) é o modo dominante na cidade.

---

## 3. Decisões técnicas (premissas que sustentam os custos)

| Área | Decisão |
|---|---|
| Hospedagem | **Railway** (PaaS) — deploy via Docker, Postgres gerenciado, SSL e backup automáticos |
| Mapas | **Google Maps** (Routes + Geocoding) |
| Pagamento | **Mercado Pago com split** — Pix QR dinâmico (**taxa 0%** para a maioria dos vendedores) |
| Cliente | App **Flutter** (passageiro + motorista), desenvolvido por terceiro |
| Documentos do motorista | Upload no app + aprovação manual (requer storage de objetos) |
| Push notification | **FCM** (Firebase) — opcional, free tier |
| CNPJ | Já existente |

### Escopo técnico (contexto — não entra no custo desta conta)
Backend existente requer hardening (Dockerfile, perfil de produção, CI/CD, integração Google Maps, health checks); integração Pix Mercado Pago + split + webhook; upload e aprovação de documentos; configuração de Remanso (pricing MOTO/CARRO); app Flutter. Comunicação em tempo real usa **SSE + polling** hoje; push (FCM) é evolução.

---

## 4. 💰 Custo de infraestrutura & ferramentas — 6 meses

### 4.1 Itens e custo semestral

| Item | Base (mediana) | 6 meses |
|---|---|---|
| Railway — **produção** (app Spring Boot ~1 GB + Postgres gerenciado) | ~US$ 30/mês | ~R$ 972 |
| Railway — **staging** (ambiente de teste leve) | ~US$ 10/mês | ~R$ 324 |
| Google Maps (Routes + Geocoding) — free 10k/SKU/mês + **cache já existente**, com ramp de volume | ~R$ 120/mês médio | ~R$ 720 |
| Storage de documentos do motorista (Cloudflare R2 / bucket) | ~R$ 30/mês | ~R$ 180 |
| E-mail transacional (Resend/SendGrid, tier básico) | ~R$ 30/mês | ~R$ 180 |
| Monitoramento de erros + uptime (Sentry/Better Stack, tier básico) | ~R$ 25/mês | ~R$ 150 |
| Domínio `.com.br` (Registro.br) | R$ 40/ano | ~R$ 40 |
| Conta Apple Developer (publicar na App Store) | US$ 99/ano | ~R$ 535 |
| Conta Google Play (taxa única) | US$ 25 | ~R$ 135 |
| Firebase Cloud Messaging (push, se adotado) | free tier | R$ 0 |
| **Subtotal infra & ferramentas** | | **~R$ 3.236** |
| Margem de variação de uso (~10% — Railway/Maps são por consumo) | | ~R$ 324 |
| **Total (com margem)** | | **~R$ 3.560** |

### 4.2 Burn mês a mês — infra & ferramentas (R$)

| Item | M1 | M2 | M3 | M4 | M5 | M6 | Total |
|---|---|---|---|---|---|---|---|
| Recorrente fixo (Railway prod+staging, storage, e-mail, monitoramento) | 301 | 301 | 301 | 301 | 301 | 301 | 1.806 |
| Google Maps (ramp com o lançamento) | 0 | 0 | 50 | 120 | 250 | 300 | 720 |
| Setup único (domínio + Apple + Google Play) | 710 | 0 | 0 | 0 | 0 | 0 | 710 |
| **Total do mês** | **1.011** | **301** | **351** | **421** | **551** | **601** | **3.236** |

- **Total semestral (infra & ferramentas): ~R$ 3.236** (ou **~R$ 3.560** com a margem de ~10%).
- **Média mensal:** ~R$ 540/mês. Pico no M1 (taxas únicas das lojas) e crescimento gradual de mapas a partir do lançamento (M4).

### 4.3 Notas que sustentam os números

- **Pagamentos = R$ 0 de custo:** o Mercado Pago Pix QR dinâmico tem taxa **0%** para a maioria dos vendedores; o processamento não pesa no piloto.
- **Mapas controlados:** o backend **já cacheia rotas e geocoding** por coordenada arredondada, reduzindo drasticamente as chamadas pagas ao Google.
- **Projeção mediana (não mínima):** inclui **staging**, **monitoramento** e **e-mail transacional** — itens que um corte agressivo eliminaria, mas que dão robustez. Não inclui tiers premium nem redundância multi-região.
- **Escalável e elástico:** Railway e Google são por consumo; se o piloto crescer além do previsto, o custo sobe de forma proporcional e previsível — daí a margem de ~10%.

---

## 5. O que NÃO está nesta conta (declarado para transparência)

- **Desenvolvimento** (backend + Pix/split + upload de documentos + app Flutter).
- **Jurídico & contábil** (enquadramento ME, termos de uso, LGPD, parecer regulatório do mototáxi).
- **Marketing & captação de motoristas.**

Estes itens existem e serão orçados à parte. Este documento isola o **custo recorrente de infraestrutura & ferramentas**.

---

## 6. Riscos de custo & mitigação

| Risco | Mitigação |
|---|---|
| Volume de mapas acima do previsto | Monitorar uso; cache já existente; OSRM self-host como plano B |
| Crescimento de tráfego | Railway escala por consumo (custo previsível); margem de ~10% absorve picos |
| Conectividade 4G no interior | Não afeta custo de infra, mas exige app leve + fallback por polling |

---

## 7. Cronograma macro (6 meses)

- **M1–M3:** build — hardening do backend, Pix + split, upload de documentos, configuração de Remanso, app Flutter.
- **M4:** lançamento público em Remanso.
- **M5–M6:** crescimento, monitoramento de custo e preparo para a 2ª cidade.

---

## 8. Fontes de preços (consulta jun/2026)

- Railway — https://docs.railway.com/pricing/plans
- Google Maps Platform — https://mapsplatform.google.com/pricing/
- Mercado Pago (Pix/QR) — https://www.mercadopago.com.br/blog/quanto-custa-receber-pagamentos-via-pix-e-codigo-qr
- Apple Developer Program (US$ 99/ano) e Google Play (US$ 25, taxa única) — taxas oficiais das lojas
