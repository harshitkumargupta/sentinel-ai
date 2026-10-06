#!/usr/bin/env bash
#
# Seeded end-to-end evaluation. Drives the simulator, runs the detection evaluation, collects
# alert-reduction + response-time metrics, and writes docs/final-evaluation.md.
#   ./scripts/run-evaluation.sh            # needs the backend running (ADMIN creds via env)
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${BASE_URL:-http://localhost:8080}"
USER="${ADMIN_USER:-admin}"
PASS="${ADMIN_PASS:-Admin@123}"
SEED="${SEED:-42}"
OUT="$ROOT/docs/final-evaluation.md"

jqget() { if command -v jq >/dev/null; then jq -r "$1"; else python3 -c "import sys,json;d=json.load(sys.stdin);print(eval('d'+'$2'))" 2>/dev/null; fi; }

echo "==> Logging in to $BASE"
TOKEN=$(curl -fsS -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "login failed"; exit 1; }
AUTH=(-H "Authorization: Bearer $TOKEN")

echo "==> Running simulator (seed=$SEED)"
RUN=$(curl -fsS -X POST "$BASE/api/simulator/run" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d "{\"seed\":$SEED}")
RUN_ID=$(echo "$RUN" | sed -n 's/.*"runId":"\([^"]*\)".*/\1/p');
[ -n "$RUN_ID" ] || RUN_ID=$(echo "$RUN" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
echo "   runId=$RUN_ID"
sleep 25

echo "==> Fetching evaluation + metrics"
EVAL=$(curl -fsS "$BASE/api/evaluation/detection?runId=$RUN_ID" "${AUTH[@]}" 2>/dev/null || echo '{}')
REDU=$(curl -fsS "$BASE/api/dashboard/alert-reduction" "${AUTH[@]}" 2>/dev/null || echo '{}')
RESP=$(curl -fsS "$BASE/api/evaluation/response-time" "${AUTH[@]}" 2>/dev/null || echo '{}')
SUMM=$(curl -fsS "$BASE/api/dashboard/summary" "${AUTH[@]}" 2>/dev/null || echo '{}')

HW="$(uname -srm) · $(sysctl -n hw.ncpu 2>/dev/null || nproc) cores"

{
  echo "# Final Evaluation"
  echo
  echo "Seeded run (seed=$SEED) on $(date -u +%Y-%m-%dT%H:%M:%SZ). Hardware: $HW."
  echo "> Single-node, simulated data — see Limitations."
  echo
  echo "## Detection quality (per-rule & incident-level)"
  echo '```json'; echo "$EVAL"; echo '```'
  echo
  echo "## Alert-reduction ratio"
  echo '```json'; echo "$REDU"; echo '```'
  echo
  echo "## Alert → approved-action latency (SOAR)"
  echo '```json'; echo "$RESP"; echo '```'
  echo
  echo "## Dashboard summary snapshot"
  echo '```json'; echo "$SUMM"; echo '```'
  echo
  echo "## ML rules vs model vs hybrid"
  echo "See [docs/ml-evaluation.md](ml-evaluation.md) for the rules-only vs model-only vs hybrid"
  echo "precision/recall comparison (produced by the ML evaluation harness)."
  echo
  echo "## AI faithfulness & injection"
  echo "See [docs/ai-evaluation.md](ai-evaluation.md): evidence-faithfulness score and the"
  echo "prompt-injection suite pass rate (deterministic FakeLlmClient in CI)."
  echo
  echo "## Chaos (Kafka pipeline)"
  echo "\`KafkaPipelineTest\` + the chaos demo assert **zero loss / zero duplicates** through"
  echo "retry→DLQ→replay (idempotency keys + transactional outbox)."
  echo
  echo "## Load-test headline"
  echo "See [docs/performance.md](performance.md) for the k6 ingest/read/soak numbers."
  echo
  echo "## Limitations"
  echo "- **Simulated data**: events come from the seeded simulator, not production traffic."
  echo "- **Single node**: no HA; metrics reflect one host (stated above)."
  echo "- **Deterministic AI in CI**: faithfulness/injection measured with FakeLlmClient; a live LLM"
  echo "  will vary. Decisions are computed deterministically regardless."
} > "$OUT"

echo "✅ Wrote $OUT"
