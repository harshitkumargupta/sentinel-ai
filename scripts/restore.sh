#!/usr/bin/env bash
#
# Restore a MySQL backup produced by backup.sh.
#   ./scripts/restore.sh backups/sentinelai-<ts>.sql.gz
set -euo pipefail

# Prefer the Compose v2 plugin; fall back to the standalone docker-compose binary.
if docker compose version >/dev/null 2>&1; then DC="docker compose"; else DC="docker-compose"; fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
DOCKER_DIR="infrastructure/docker"
ENV_FILE="$DOCKER_DIR/.env"
COMPOSE_FILE="$DOCKER_DIR/docker-compose.prod.yml"

DUMP="${1:?usage: restore.sh <backup.sql.gz>}"
[ -f "$DUMP" ] || { echo "Backup not found: $DUMP"; exit 1; }
[ -f "$ENV_FILE" ] || { echo "Missing $ENV_FILE"; exit 1; }
# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a

echo "==> Restoring $DUMP into database '${DB_NAME:-sentinelai}'"
gunzip -c "$DUMP" | $DC --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
  sh -c "exec mysql -u root -p\"\$MYSQL_ROOT_PASSWORD\" ${DB_NAME:-sentinelai}"

echo "✅ Restore complete. Restart the backend if it caches state:  docker compose -f $COMPOSE_FILE restart backend"
