#!/usr/bin/env bash
#
# Zero-downtime-ish deploy with automatic rollback.
#   ./scripts/deploy.sh <tag>
#
# Pulls the given image tag, records the currently-deployed tag, brings the stack up, waits for
# health, runs a smoke test (login → ingest one event → fetch it) and — on ANY failure — rolls back
# to the previous tag. Secrets come from infrastructure/docker/.env and are never printed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
DOCKER_DIR="infrastructure/docker"
ENV_FILE="$DOCKER_DIR/.env"
COMPOSE_FILE="$DOCKER_DIR/docker-compose.prod.yml"
STATE_FILE="$DOCKER_DIR/.deploy-state"

NEW_TAG="${1:?usage: deploy.sh <tag>}"
[ -f "$ENV_FILE" ] || { echo "Missing $ENV_FILE (copy .env.example)"; exit 1; }

# Prefer the Compose v2 plugin; fall back to the standalone docker-compose binary.
if docker compose version >/dev/null 2>&1; then DC=(docker compose); else DC=(docker-compose); fi

# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a
HTTP_PORT="${HTTP_PORT:-80}"
BASE_URL="http://localhost:${HTTP_PORT}"
PROFILES="${COMPOSE_PROFILES:-}"

compose() { TAG="$1" "${DC[@]}" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ${PROFILES:+--profile $PROFILES} "${@:2}"; }

PREV_TAG="$(cat "$STATE_FILE" 2>/dev/null || echo "${TAG:-latest}")"
echo "Deploying tag '$NEW_TAG' (previous: '$PREV_TAG')"

wait_healthy() {
  local svc="$1" tries=40
  local cid
  cid="$(compose "$NEW_TAG" ps -q "$svc")"
  [ -n "$cid" ] || { echo "service $svc not found"; return 1; }
  for ((i=1; i<=tries; i++)); do
    local status
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || echo unknown)"
    case "$status" in
      healthy|running) [ "$status" = healthy ] && { echo "  $svc: healthy"; return 0; } ;;
      exited|dead) echo "  $svc: $status"; return 1 ;;
    esac
    sleep 5
  done
  echo "  $svc: timed out waiting for health"; return 1
}

smoke_test() {
  echo "==> Smoke test against $BASE_URL"
  local user="${ADMIN_USERNAME:-admin}" pass="${ADMIN_PASSWORD:?ADMIN_PASSWORD required for smoke test}"
  local token
  token="$(curl -fsS -X POST "$BASE_URL/api/auth/login" -H 'Content-Type: application/json' \
      -d "{\"username\":\"$user\",\"password\":\"$pass\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
  [ -n "$token" ] || { echo "  login failed"; return 1; }
  echo "  login ok"

  local marker="smoke-$(date +%s)"
  local id
  id="$(curl -fsS -X POST "$BASE_URL/api/events" -H "Authorization: Bearer $token" \
      -H 'Content-Type: application/json' \
      -d "{\"eventType\":\"OTHER\",\"severity\":\"LOW\",\"username\":\"$marker\"}" \
      | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)"
  [ -n "$id" ] || { echo "  ingest failed"; return 1; }
  echo "  ingest ok (event id=$id)"

  curl -fsS "$BASE_URL/api/events/$id" -H "Authorization: Bearer $token" | grep -q "$marker" \
    || { echo "  fetch-back failed"; return 1; }
  echo "  fetch ok — smoke test passed"
}

# The whole attempt runs inside a function invoked from `if`, so set -e does NOT abort on a failed
# pull/up/health/smoke — any failure falls through to the rollback branch.
deploy_attempt() {
  echo "==> Pulling images (best-effort; local images are skipped)"
  compose "$NEW_TAG" pull || echo "pull skipped/failed (expected for locally-built images)"
  echo "==> Starting stack"
  compose "$NEW_TAG" up -d || return 1
  wait_healthy mysql && wait_healthy backend && wait_healthy frontend && smoke_test
}

if deploy_attempt; then
  echo "$NEW_TAG" > "$STATE_FILE"
  echo "✅ Deploy of '$NEW_TAG' succeeded."
else
  echo "❌ Deploy of '$NEW_TAG' failed — rolling back to '$PREV_TAG'."
  "$ROOT/scripts/rollback.sh" "$PREV_TAG"
  exit 1
fi
