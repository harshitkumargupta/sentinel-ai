#!/usr/bin/env bash
#
# SentinelAI :: ingest benchmark (sync vs Kafka).
#
# Fires a burst of ingest requests at POST /api/events/ingest and reports throughput (events/sec)
# and p95 client-observed ingest latency. Run it once against a backend started with
# KAFKA_ENABLED=false (synchronous path) and once with KAFKA_ENABLED=true (Kafka path), then compare
# — the Kafka path should show lower ingest latency (it only persists + enqueues) at the cost of
# asynchronous end-to-end detection.
#
# Usage:
#   MODE=sync  N=10000 C=32 ADMIN_USER=admin ADMIN_PASS='Admin@123' scripts/benchmark.sh
#   MODE=kafka N=10000 C=32 scripts/benchmark.sh
#
set -euo pipefail

API="${API_BASE:-http://localhost:8080/api}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-Admin@123}"
N="${N:-10000}"
C="${C:-32}"
MODE="${MODE:-unknown}"

TOKEN=$(curl -fsS -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")

MODE="$MODE" API="$API" TOKEN="$TOKEN" N="$N" C="$C" python3 - <<'PY'
import os, json, time, urllib.request, concurrent.futures as cf

api, token, n, c, mode = os.environ["API"], os.environ["TOKEN"], int(os.environ["N"]), int(os.environ["C"]), os.environ["MODE"]
url = api + "/events/ingest"
hdr = {"Content-Type": "application/json", "Authorization": "Bearer " + token}

def one(i):
    body = json.dumps({"sourceType": "auth",
                       "payload": {"username": f"bench-{i%500}", "sourceIp": f"10.1.{(i//256)%256}.{i%256}", "success": False}}).encode()
    t0 = time.perf_counter()
    req = urllib.request.Request(url, data=body, headers=hdr, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            r.read()
    except Exception as e:
        return None
    return time.perf_counter() - t0

# warm up
for i in range(min(50, n)):
    one(i)

lat = []
start = time.perf_counter()
with cf.ThreadPoolExecutor(max_workers=c) as ex:
    for d in ex.map(one, range(n)):
        if d is not None:
            lat.append(d)
wall = time.perf_counter() - start

lat.sort()
ok = len(lat)
def pct(p): return lat[min(ok-1, int(p/100*ok))] * 1000 if ok else float('nan')
print(f"mode={mode} requests={n} ok={ok} concurrency={c}")
print(f"throughput={ok/wall:,.0f} events/sec  wall={wall:,.2f}s")
print(f"latency_ms p50={pct(50):.1f} p95={pct(95):.1f} p99={pct(99):.1f} max={lat[-1]*1000:.1f}")
PY
