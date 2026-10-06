#!/usr/bin/env bash
#
# Roll the stack back to a known-good tag.
#   ./scripts/rollback.sh <tag>
set -euo pipefail

# Prefer the Compose v2 plugin; fall back to the standalone docker-compose binary.
if docker compose version >/dev/null 2>&1; then DC="docker compose"; else DC="docker-compose"; fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
DOCKER_DIR="infrastructure/docker"
ENV_FILE="$DOCKER_DIR/.env"
COMPOSE_FILE="$DOCKER_DIR/docker-compose.prod.yml"
STATE_FILE="$DOCKER_DIR/.deploy-state"

TARGET_TAG="${1:?usage: rollback.sh <tag>}"
[ -f "$ENV_FILE" ] || { echo "Missing $ENV_FILE"; exit 1; }

echo "==> Rolling back to tag '$TARGET_TAG'"
TAG="$TARGET_TAG" $DC --env-file "$ENV_FILE" -f "$COMPOSE_FILE" pull \
  || echo "pull skipped (local image)"
TAG="$TARGET_TAG" $DC --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d

echo "$TARGET_TAG" > "$STATE_FILE"
echo "✅ Rolled back to '$TARGET_TAG'. Verify health with: docker compose -f $COMPOSE_FILE ps"
