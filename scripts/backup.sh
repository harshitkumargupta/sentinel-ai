#!/usr/bin/env bash
#
# Timestamped MySQL backup with retention.
#   ./scripts/backup.sh            # writes backups/sentinelai-<ts>.sql.gz, prunes old ones
#
# Retention: keeps the newest RETENTION_DAYS of backups (default 7). Secrets read from .env only.
set -euo pipefail

# Prefer the Compose v2 plugin; fall back to the standalone docker-compose binary.
if docker compose version >/dev/null 2>&1; then DC="docker compose"; else DC="docker-compose"; fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
DOCKER_DIR="infrastructure/docker"
ENV_FILE="$DOCKER_DIR/.env"
COMPOSE_FILE="$DOCKER_DIR/docker-compose.prod.yml"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"

[ -f "$ENV_FILE" ] || { echo "Missing $ENV_FILE"; exit 1; }
# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a
mkdir -p "$BACKUP_DIR"

TS="$(date +%Y%m%d-%H%M%S)"
OUT="$BACKUP_DIR/sentinelai-$TS.sql.gz"

echo "==> Dumping database to $OUT"
$DC --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
  sh -c "exec mysqldump -u root -p\"\$MYSQL_ROOT_PASSWORD\" --single-transaction --routines --triggers ${DB_NAME:-sentinelai}" \
  | gzip > "$OUT"

echo "==> Pruning backups older than $RETENTION_DAYS days"
find "$BACKUP_DIR" -name 'sentinelai-*.sql.gz' -type f -mtime "+$RETENTION_DAYS" -print -delete || true

echo "✅ Backup complete: $OUT ($(du -h "$OUT" | cut -f1))"
