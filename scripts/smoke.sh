#!/usr/bin/env sh
# Smoke nao destrutivo de um ambiente (RNF06). Nao cria conta nem corrida:
# so le, abre um desafio de telefone (expira sozinho) e, com credenciais de
# uma conta de smoke, confere login e perfil.
#
#   scripts/smoke.sh https://api.exemplo.com
#   SMOKE_EMAIL=smoke@x SMOKE_PASSWORD=... scripts/smoke.sh https://api.exemplo.com
#
# Variaveis opcionais: HEALTH_ATTEMPTS (10), HEALTH_INTERVAL (15 s). O plano
# free do Render hiberna; a primeira resposta pode levar mais de um minuto.
# Sai com 1 se qualquer checagem falhar. Requer apenas sh + curl.

BASE="${1:?uso: scripts/smoke.sh <url-base>}"
BASE="${BASE%/}"
ATTEMPTS="${HEALTH_ATTEMPTS:-10}"
INTERVAL="${HEALTH_INTERVAL:-15}"
BODY="$(mktemp)"
FAILED=0
trap 'rm -f "$BODY"' EXIT

now() { date -u +%Y-%m-%dT%H:%M:%SZ; }

# call METODO CAMINHO [TOKEN] [JSON] -> imprime o HTTP code; corpo em $BODY
call() {
    path="$2" token="${3:-}" json="${4:-}"
    set -- -s -o "$BODY" -w '%{http_code}' --max-time 20 -X "$1"
    if [ -n "$token" ]; then set -- "$@" -H "Authorization: Bearer $token"; fi
    if [ -n "$json" ]; then set -- "$@" -H 'Content-Type: application/json' -d "$json"; fi
    # Sem conexao o curl ja escreve 000 no -w; o || true so evita abortar.
    curl "$@" "$BASE$path" || true
}

# check NOME ESPERADO OBTIDO [TRECHO_OBRIGATORIO_NO_CORPO]
check() {
    if [ "$2" = "$3" ] && { [ -z "${4:-}" ] || grep -q "$4" "$BODY"; }; then
        echo "PASS  $1 ($3)"
    else
        echo "FAIL  $1 (esperado $2, veio $3) $(head -c 200 "$BODY")"
        FAILED=1
    fi
}

field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" "$BODY"; }

echo "Smoke $BASE em $(now)"

i=1
while :; do
    code=$(call GET /actuator/health)
    echo "      health tentativa $i/$ATTEMPTS: HTTP $code ($(now))"
    if [ "$code" = "200" ] && grep -q '"status":"UP"' "$BODY"; then break; fi
    if [ "$i" -ge "$ATTEMPTS" ]; then break; fi
    i=$((i + 1)); sleep "$INTERVAL"
done
check "health" 200 "$code" '"status":"UP"'
check "readiness (banco)" 200 "$(call GET /actuator/health/readiness)" '"status":"UP"'
check "info" 200 "$(call GET /actuator/info)" '"build"'
echo "      versao $(field version) commit $(field commit)"
check "cidades atendidas" 200 "$(call GET /cities)" '"slug"'
check "termos vigentes" 200 "$(call GET /legal/terms)" '"version"'
check "desafio de telefone" 201 "$(call POST /auth/phone/challenge)" '"waLink"'
check "perfil sem token" 401 "$(call GET /users/me)"

if [ -n "${SMOKE_EMAIL:-}" ] && [ -n "${SMOKE_PASSWORD:-}" ]; then
    check "login" 200 "$(call POST /auth/login '' "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"$SMOKE_PASSWORD\"}")" '"token"'
    TOKEN="$(field token)"
    check "perfil autenticado" 200 "$(call GET /users/me "$TOKEN")" '"email"'
    check "historico de corridas" 200 "$(call GET /rides/history "$TOKEN")"
else
    echo "SKIP  login/perfil (defina SMOKE_EMAIL e SMOKE_PASSWORD)"
fi

if [ "$FAILED" -eq 0 ]; then echo "OK    smoke passou"; else echo "ERRO  smoke falhou"; fi
exit "$FAILED"
