# Deploy automatico na VPS (Oracle Cloud Always Free)

Push na `main` → testes → imagem ARM64 no GHCR → a VPS se atualiza sozinha →
health check confirma (ou faz rollback). Custo de infraestrutura: **R$ 0**.

```
git push origin main
   │
   ├─ [test]   mvn -B verify ......................... ~1 min, sem Docker/Postgres
   ├─ [build]  buildx --platform linux/arm64
   │           push ghcr.io/cristianneves/unimove:<sha> e :latest
   └─ [deploy] scp compose/Caddyfile/deploy.sh -> /opt/unimove
               ssh  ./deploy.sh <sha>
                    ├─ docker compose pull api && up -d
                    ├─ poll /actuator/health (ate 2 min)
                    └─ falhou? volta para a tag anterior e falha o job
```

Arquivos envolvidos: [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml),
[`Dockerfile`](../Dockerfile), [`docker-compose.prod.yml`](../docker-compose.prod.yml),
[`Caddyfile`](../Caddyfile), [`scripts/deploy.sh`](../scripts/deploy.sh),
[`src/main/resources/application-prod.yml`](../src/main/resources/application-prod.yml).

---

## 1. Criar a instancia na Oracle Cloud

1. Conta em [cloud.oracle.com](https://cloud.oracle.com). Escolha a regiao
   **Brazil East (Sao Paulo)** — `sa-saopaulo-1`. **A regiao nao muda depois**;
   errar aqui obriga a comecar de novo.
2. *Compute → Instances → Create instance*:
   - **Image:** Ubuntu 24.04 (aarch64)
   - **Shape:** `VM.Standard.A1.Flex` — 4 OCPU / 24 GB RAM (o teto do Always Free)
   - **Boot volume:** 50 GB bastam (o free tier da 200 GB no total)
   - **SSH keys:** salve o par oferecido; e por ele que voce entra pela 1a vez
3. Anote o **IP publico** e marque o IP como *reserved* para nao mudar.

> **"Out of host capacity"** — o erro mais comum. A capacidade ARM em Sao Paulo
> vive esgotada. Tente em horarios diferentes ao longo de alguns dias; costuma
> sair. Se nao sair, da para usar o shape x86 `VM.Standard.E2.1.Micro` — mas so
> tem 1 GB de RAM (apertado para JVM + Postgres juntos) e ai e preciso trocar
> `platforms: linux/arm64` por `linux/amd64` no workflow.

## 2. Abrir as portas 80 e 443 — **em dois lugares**

Esta e a pegadinha classica da Oracle: a imagem Ubuntu vem com regras de
`iptables` proprias, **alem** da Security List da rede virtual. Esquecer a
segunda faz a porta parecer aberta no painel e continuar recusando conexao.

**a) Na VCN** — *Networking → Virtual Cloud Networks → sua VCN → Security Lists
→ Default → Add Ingress Rules*: origem `0.0.0.0/0`, TCP, portas de destino
`80` e `443`.

**b) Na VM** (via SSH):

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 3. Preparar a VPS

```bash
# Docker + cliente psql (para os backups)
sudo apt update && sudo apt install -y docker.io docker-compose-v2 postgresql-client
sudo usermod -aG docker ubuntu && newgrp docker

sudo mkdir -p /opt/unimove/backups
sudo chown -R ubuntu:ubuntu /opt/unimove
```

**Chave SSH exclusiva do deploy** (nao reaproveite a chave pessoal — esta vai
para dentro do GitHub):

```bash
ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N "" -C "github-actions"
cat ~/.ssh/github_deploy.pub >> ~/.ssh/authorized_keys
cat ~/.ssh/github_deploy          # <- conteudo vai para o secret VPS_SSH_KEY
```

**Login no GHCR** — o pacote e privado (codigo comercial compilado). Crie um
[PAT classic](https://github.com/settings/tokens) com **apenas** o escopo
`read:packages`:

```bash
echo "<PAT>" | docker login ghcr.io -u cristianneves --password-stdin
```

## 4. Criar `/opt/unimove/.env`

Os segredos ficam **so aqui**. O GitHub nunca os conhece — rotacionar um
segredo nao exige redeploy, e um vazamento do repositorio nao vaza producao.

```bash
cat > /opt/unimove/.env <<'EOF'
DOMAIN=unimove.duckdns.org
DATABASE_PASSWORD=<senha forte>
JWT_SECRET=<openssl rand -base64 48>

PHONE_VERIFICATION_CHANNEL=WHATSAPP
WHATSAPP_BUSINESS_NUMBER=55xxxxxxxxxxx
WHATSAPP_APP_SECRET=<App Secret do app Meta>
WHATSAPP_WEBHOOK_VERIFY_TOKEN=<valor livre, repetido no painel da Meta>

GOOGLE_LOGIN_ENABLED=true
GOOGLE_CLIENT_IDS=<client-id-web>,<client-id-ios>
EOF
chmod 600 /opt/unimove/.env
```

`IMAGE_TAG` **nao** vai aqui: o `deploy.sh` escreve e atualiza essa linha sozinho.

Se `DATABASE_PASSWORD`, `JWT_SECRET` ou `DOMAIN` faltarem, o `docker compose`
recusa subir (`${VAR:?...}`). Se o `JWT_SECRET` chegasse vazio ate a aplicacao,
o `application-prod.yml` aborta o startup — de proposito: sem isso a API subiria
assinando token com o segredo de desenvolvimento que esta neste repositorio.

## 5. DuckDNS

1. Em [duckdns.org](https://www.duckdns.org) (login com GitHub), registre
   `unimove` e aponte para o IP publico da VPS.
2. Cron de atualizacao (barato e evita surpresa se o IP mudar):

```bash
(crontab -l 2>/dev/null; echo "*/5 * * * * curl -s 'https://www.duckdns.org/update?domains=unimove&token=<TOKEN>&ip=' >/dev/null") | crontab -
```

O Caddy pede o certificado Let's Encrypt sozinho no primeiro acesso e renova
sem intervencao. Para migrar depois para dominio proprio, basta mudar `DOMAIN`
no `.env` e reiniciar o Caddy.

## 6. Secrets no GitHub

*Settings → Secrets and variables → Actions → New repository secret*:

| Secret | Valor |
|---|---|
| `VPS_HOST` | IP publico da VPS |
| `VPS_USER` | `ubuntu` |
| `VPS_SSH_KEY` | conteudo de `~/.ssh/github_deploy` (a chave **privada**, inteira) |
| `VPS_SSH_KNOWN_HOSTS` | saida de `ssh-keyscan <IP>` rodado na sua maquina |

O push da imagem usa o `GITHUB_TOKEN` automatico — nao precisa de PAT no CI.

## 7. Primeiro deploy

```bash
# Na VPS: sobe postgres e caddy e deixa tudo pronto
cd /opt/unimove && docker compose -f docker-compose.prod.yml up -d postgres
```

Depois e so mergear na `main`. O `deploy.sh` cuida do resto. Acompanhe em
*Actions* no GitHub e, na VPS, com `docker compose -f docker-compose.prod.yml logs -f api`
— e ali que as 20 migrations do Flyway rodam pela primeira vez de verdade.

**Logo apos o primeiro deploy, troque a senha do admin** semeado em
`V2__seed_admin.sql`: o hash BCrypt esta versionado neste repositorio.

## 8. Backup do banco

```bash
mkdir -p /opt/unimove/backups
cat > /opt/unimove/backup.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
cd /opt/unimove
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U unimove unimove | gzip > "backups/unimove-$(date +%F).sql.gz"
find backups -name 'unimove-*.sql.gz' -mtime +7 -delete
EOF
chmod +x /opt/unimove/backup.sh
(crontab -l 2>/dev/null; echo "0 4 * * * /opt/unimove/backup.sh") | crontab -
```

Restaurar:

```bash
gunzip -c backups/unimove-2026-08-04.sql.gz | \
  docker compose -f docker-compose.prod.yml exec -T postgres psql -U unimove -d unimove
```

> Os backups ficam no mesmo disco da VPS. Para o piloto serve; antes do
> lancamento vale copiar o `.sql.gz` para fora (R2/S3).

---

## Operacao

**Rollback manual** — o `deploy.sh` ja volta sozinho quando o health check
falha. Para voltar a uma versao mais antiga na mao:

```bash
cd /opt/unimove
sed -i 's|^IMAGE_TAG=.*|IMAGE_TAG=<sha-antigo>|' .env
docker compose -f docker-compose.prod.yml up -d
```

Migration de banco **nao** volta com o rollback. Se a versao quebrada tiver
rodado um `V{n}`, o schema fica na frente do codigo antigo — corrija com uma
`V{n+1}` nova, nunca editando a migration ja aplicada.

**Ver o que esta rodando:** `grep IMAGE_TAG /opt/unimove/.env`

**Health:** `curl https://unimove.duckdns.org/actuator/health` → `{"status":"UP"}`

**Downtime por deploy:** ~30 s. O container e recriado; nao ha blue-green.
Conexoes SSE abertas (mural, chat) caem e o app precisa reconectar.

---

## Limites conhecidos

| Limite | Por que | Quando resolver |
|---|---|---|
| Instancia unica | Os hubs de SSE sao em memoria (`docs/analise-mvp.md`) | Ao escalar: pub/sub via Redis |
| ~30 s de downtime por deploy | Recreate do container | Blue-green atras do Caddy |
| Sem staging | Um ambiente so, como no MVP | Antes de abrir para usuarios reais |
| Observabilidade so no `/health` | Sem Micrometer/Prometheus | Pos-MVP |
| OSRM na demo publica | `router.project-osrm.org`, sem SLA | Self-host na propria VPS (sobra RAM) |
| Backup no mesmo disco | Simplicidade | Copia externa antes do go-live |
