#!/usr/bin/env bash
#
# SentinelAI :: Kafka pipeline chaos demo.
#
# Pauses the detection consumer, publishes a burst of events, shows the consumer lag growing,
# resumes the consumer, waits for the backlog to drain, and then verifies zero loss and zero
# duplicates by comparing: events persisted == messages processed by detection == burst size,
# with an empty DLQ and zero residual lag.
#
# Requires: a running backend with the Kafka pipeline enabled (KAFKA_ENABLED=true) and Kafka up
# (docker compose -f infrastructure/docker/docker-compose.yml up -d), plus curl and python3.
#
# Usage:
#   ADMIN_USER=admin ADMIN_PASS='Password@123' scripts/chaos-demo.sh [burst_size]
#
set -euo pipefail

API="${API_BASE:-http://localhost:8080/api}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-Password@123}"
N="${1:-${BURST:-2000}}"
LISTENER="${LISTENER:-detection}"
GROUP="${GROUP:-sentinel-detection}"
RAW_GROUP="${RAW_GROUP:-sentinel-raw-ingest}"

# Extract a value from JSON on stdin: jqget <python-expression over variable `d`>
jqget() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }

echo "==> Logging in as $ADMIN_USER"
TOKEN=$(curl -fsS -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" | jqget "d['data']['accessToken']")
AUTH=(-H "Authorization: Bearer $TOKEN")

status() { curl -fsS "${AUTH[@]}" "$API/admin/pipeline-status"; }
events_total() { curl -fsS "${AUTH[@]}" "$API/events?page=0&size=1" | jqget "d['data']['totalElements']"; }

echo "==> Baseline"
S0=$(status)
PROC0=$(echo "$S0" | jqget "d['data']['processedByGroup'].get('$GROUP',0)")
RAW0=$(echo "$S0" | jqget "d['data']['processedByGroup'].get('$RAW_GROUP',0)")
DLQ0=$(echo "$S0" | jqget "d['data']['dlqSize']")
EV0=$(events_total)
echo "    events=$EV0 processed[$GROUP]=$PROC0 processed[$RAW_GROUP]=$RAW0 dlq=$DLQ0"

echo "==> Pausing consumer '$LISTENER'"
curl -fsS -X POST "${AUTH[@]}" "$API/admin/chaos/pause?listener=$LISTENER" >/dev/null

echo "==> Publishing burst of $N events to events.raw"
PUBLISHED=$(curl -fsS -X POST "${AUTH[@]}" "$API/admin/chaos/burst?count=$N" | jqget "d['data']['published']")
echo "    published=$PUBLISHED"

echo "==> Watching lag grow (consumer paused)"
for _ in 1 2 3; do
  sleep 1
  LAG=$(status | jqget "d['data']['consumerLag'].get('$GROUP',0)")
  echo "    lag[$GROUP]=$LAG"
done

echo "==> Resuming consumer '$LISTENER'"
curl -fsS -X POST "${AUTH[@]}" "$API/admin/chaos/resume?listener=$LISTENER" >/dev/null

echo "==> Waiting for backlog to drain"
for _ in $(seq 1 120); do
  S=$(status)
  LAG=$(echo "$S" | jqget "d['data']['consumerLag'].get('$GROUP',0)")
  OUT=$(echo "$S" | jqget "d['data']['outboxBacklog']")
  echo "    lag[$GROUP]=$LAG outboxBacklog=$OUT"
  [ "$LAG" -eq 0 ] && [ "$OUT" -eq 0 ] && break
  sleep 1
done

echo "==> Final state"
S1=$(status)
PROC1=$(echo "$S1" | jqget "d['data']['processedByGroup'].get('$GROUP',0)")
RAW1=$(echo "$S1" | jqget "d['data']['processedByGroup'].get('$RAW_GROUP',0)")
DLQ1=$(echo "$S1" | jqget "d['data']['dlqSize']")
LAG1=$(echo "$S1" | jqget "d['data']['consumerLag'].get('$GROUP',0)")
EV1=$(events_total)

D_EV=$((EV1 - EV0)); D_PROC=$((PROC1 - PROC0)); D_RAW=$((RAW1 - RAW0)); D_DLQ=$((DLQ1 - DLQ0))
echo "    Δevents=$D_EV  Δprocessed[$GROUP]=$D_PROC  Δprocessed[$RAW_GROUP]=$D_RAW  ΔdlqSize=$D_DLQ  residualLag=$LAG1"

echo "==> Verification"
ok=1
chk() { if [ "$1" -eq "$2" ]; then echo "    PASS: $3 ($1)"; else echo "    FAIL: $3 (got $1, want $2)"; ok=0; fi; }
chk "$PUBLISHED" "$N" "all events published"
chk "$D_RAW" "$N" "raw-ingest processed every event (no loss, no dup)"
chk "$D_PROC" "$N" "detection processed every event (no loss, no dup)"
chk "$D_EV" "$N" "every event persisted"
chk "$D_DLQ" 0 "zero dead letters"
chk "$LAG1" 0 "lag fully drained"

if [ "$ok" -eq 1 ]; then
  echo "==> RESULT: zero loss, zero duplicates ✅"
else
  echo "==> RESULT: discrepancy detected ❌"; exit 1
fi
