#!/usr/bin/env bash
# One-command SentinelAI demo (keyless, offline after the first build).
#   ./scripts/demo.sh up | down | reset | logs | status
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="$ROOT/infrastructure/docker/docker-compose.demo.yml"
PORT="${DEMO_PORT:-8088}"

if docker compose version >/dev/null 2>&1; then
  DC=(docker compose -f "$COMPOSE_FILE")
elif command -v docker-compose >/dev/null 2>&1; then
  DC=(docker-compose -f "$COMPOSE_FILE")
else
  echo "Docker Compose is required (Docker Desktop, colima + docker-compose, or similar)." >&2
  exit 1
fi

wait_healthy() {
  echo "Waiting for SentinelAI on http://localhost:$PORT ..."
  for _ in $(seq 1 90); do
    if curl -fsS "http://localhost:$PORT/api/health" >/dev/null 2>&1; then
      echo
      echo "SentinelAI demo is up:  http://localhost:$PORT"
      echo "Use the 'Login as Admin / Analyst / Viewer' buttons on the login page."
      echo "Then: Admin → Demo Center → Seed Sample Data, and run a scenario."
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for the backend. Check: $0 logs" >&2
  return 1
}

case "${1:-up}" in
  up)     "${DC[@]}" up -d --build && wait_healthy ;;
  down)   "${DC[@]}" down ;;
  reset)  "${DC[@]}" down -v ;;
  logs)   "${DC[@]}" logs -f --tail=200 ;;
  status) "${DC[@]}" ps ;;
  *) echo "usage: $0 [up|down|reset|logs|status]" >&2; exit 2 ;;
esac
