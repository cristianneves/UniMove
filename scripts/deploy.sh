#!/usr/bin/env bash
#
# Roda NA VPS, chamado por SSH pelo .github/workflows/deploy.yml.
# Uso: ./deploy.sh <tag-da-imagem>   (a tag e o SHA do commit)
#
# Troca a tag da imagem, sobe, espera o /actuator/health responder UP e,
# se nao responder, volta para a tag que estava rodando antes.
set -euo pipefail

TAG="${1:?uso: ./deploy.sh <tag-da-imagem>}"
cd "$(dirname "$0")"

COMPOSE=(docker compose -f docker-compose.prod.yml)
ENV_FILE=.env
HEALTH_URL="http://127.0.0.1:8080/actuator/health"
HEALTH_RETRIES=40   # 40 x 3s = 2min; o start_period do container e de 60s
HEALTH_INTERVAL=3

[[ -f "$ENV_FILE" ]] || { echo "ERRO: $PWD/$ENV_FILE nao existe. Ver docs/deploy-vps.md"; exit 1; }

# Tag que esta rodando agora. Vazia no primeiro deploy.
PREVIOUS_TAG="$(sed -n 's/^IMAGE_TAG=//p' "$ENV_FILE" | head -1)"

set_tag() {
  if grep -q '^IMAGE_TAG=' "$ENV_FILE"; then
    sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=$1|" "$ENV_FILE"
  else
    echo "IMAGE_TAG=$1" >> "$ENV_FILE"
  fi
}

wait_healthy() {
  local i
  for ((i = 1; i <= HEALTH_RETRIES; i++)); do
    if curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "health OK apos $((i * HEALTH_INTERVAL))s"
      return 0
    fi
    sleep "$HEALTH_INTERVAL"
  done
  return 1
}

echo "==> deploy $TAG (anterior: ${PREVIOUS_TAG:-<nenhuma>})"
set_tag "$TAG"
"${COMPOSE[@]}" pull api
"${COMPOSE[@]}" up -d

if wait_healthy; then
  echo "==> deploy concluido"
  # So remove imagem sem uso e com mais de 30 dias: a imagem anterior (que
  # ainda tem tag de SHA) sobrevive e continua disponivel para rollback.
  docker image prune -af --filter "until=720h" >/dev/null || true
  exit 0
fi

echo "==> ERRO: a API nao ficou saudavel. Ultimas linhas do log:"
"${COMPOSE[@]}" logs --tail=120 api || true

# No primeiro deploy nao ha para onde voltar. E voltar para a mesma tag
# so repetiria a falha.
if [[ -z "$PREVIOUS_TAG" || "$PREVIOUS_TAG" == "$TAG" ]]; then
  echo "==> Sem versao anterior para rollback. A stack fica no ar com $TAG (quebrada)."
  exit 1
fi

echo "==> Rollback para $PREVIOUS_TAG"
set_tag "$PREVIOUS_TAG"
"${COMPOSE[@]}" up -d
if wait_healthy; then
  echo "==> Rollback concluido: producao voltou para $PREVIOUS_TAG"
else
  echo "==> ATENCAO: o rollback tambem nao ficou saudavel. Producao esta fora do ar."
fi
exit 1
