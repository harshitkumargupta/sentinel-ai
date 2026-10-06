# Performance — caching benchmark (Phase 10)

Dashboard read endpoints (`summary`, `alert-reduction`, `mitre-coverage`, `recent-incidents`)
are served **cache-aside** via Redis with a short TTL and explicit event-driven invalidation.
Keys are tenant-scoped (`dash:org:{org}:site:{site}:{name}`) so caches never leak across orgs/sites.

## Method

- Backend on `dev` profile, Redis enabled (local `redis-server`), seeded via the attack simulator.
- **Uncached:** before each sample the dashboard cache is invalidated (an incident mutation fires
  `IncidentsChangedEvent`), forcing a full DB recompute (~16 aggregate COUNT queries). 25 samples.
- **Cached:** the same endpoint hit repeatedly against a warm cache. 60 samples.
- Latency measured with `curl -w %{time_total}`; percentiles computed over the sample set.

## Results — `GET /api/dashboard/summary`

| Scenario | p50 | p95 |
|----------|-----|-----|
| Uncached (recompute from MySQL) | 10.9 ms | 13.9 ms |
| Cached (Redis hit)              | 2.5 ms  | 3.2 ms  |

**~4.3× faster at p95** (13.9 ms → 3.2 ms). Observed cache hit-rate during the warm run: ~0.71
across the mixed workload (invalidations included), reported live at `GET /api/admin/cache-stats`.

## Resilience

With Redis killed mid-run (`redis-cli shutdown`), every endpoint kept returning `200` from the
in-memory fallback (cache, sliding-window store, and rate limiter all degrade gracefully). The only
cost is a one-off ~200 ms Lettuce connect timeout per call until the breaker-style gateway logs the
outage once and subsequent calls short-circuit to the fallback. Failures are counted under the
`sentinel.redis.failures` meter.

> Numbers are from a laptop dev box with a small seeded dataset; absolute values scale with data
> volume, but the cached/uncached ratio is the point of interest.

---

# Performance — sync vs Kafka ingestion (Phase 11)

`POST /api/events/ingest` can run in two modes. **Synchronous** (`kafka.enabled=false`) persists the
event and runs the full detection → correlation → notification chain inline before responding.
**Kafka** (`kafka.enabled=true`) persists the event and an outbox row and returns `202 Accepted`,
with detection happening asynchronously down the pipeline.

## Method

- Backend on `dev` profile against local MySQL; rate limiting disabled for the run.
- `scripts/benchmark.sh` fires **10,000** ingest requests at concurrency **24** and records
  client-observed latency; throughput is `ok / wall-clock`. Warm-up of 50 requests excluded.
- Run once per mode (restarting the backend between modes). `BRUTE_FORCE` rule enabled so the sync
  path does real detection work.

## Results (10,000 requests, concurrency 24, all 10,000 OK)

| Mode  | Throughput   | p50     | p95     | p99     | max      |
|-------|--------------|---------|---------|---------|----------|
| sync  | 164 ev/sec   | 172.8ms | 319.6ms | 392.6ms | 586.0ms  |
| kafka | 648 ev/sec   | 33.9ms  | 65.0ms  | 95.6ms  | 288.2ms  |

**Kafka ingestion is ~4× the throughput and ~5× lower p95 latency** (319.6 ms → 65.0 ms), because
the request does only a persist + enqueue instead of the whole detection chain.

## Tradeoff

The sync path gives **immediate** detection — by the time `ingest` returns, any alert/incident
already exists — at the cost of high, detection-bound ingest latency that collapses under bursts.

The Kafka path gives **low, predictable ingest latency** and back-pressure/buffering under load, at
the cost of **eventual** detection: there is a short end-to-end delay (relay publish + consumer
processing, typically well under a second on this setup) before an incident appears. For a SOC
ingesting bursty telemetry, fast durable ingest plus asynchronous, independently-scalable detection
is the better tradeoff — and if the broker is down, ingestion degrades gracefully back to the
synchronous path rather than failing.

> Laptop dev box, small seeded dataset and a single-node KRaft broker; absolute values scale with
> hardware and data volume, but the sync-vs-Kafka ratio is the point of interest.

---

# Load tests (k6) — Phase 17

Scripts in [`load-tests/`](../load-tests/). Thresholds are declared per scenario (pass/fail).

**Hardware:** Apple A18 Pro, 6 cores, **8 GB RAM**, macOS 26. Dev profile.
**Important caveat:** these numbers were captured with the backend JVM **and** the full infra
(MySQL + Redis + Kafka in colima) **and** Prometheus + Grafana + a browser all on the *same* 8 GB
machine. That co-location starves the write path; a dedicated host (or the prod Compose stack with
resource limits) performs materially better. Reads, which are cache-served, are largely unaffected.

| Scenario | Load | Throughput | p95 | Failures | Threshold |
|----------|------|-----------:|----:|---------:|-----------|
| Dashboard read mix (cached) | 50 VUs, 25s | **1,237 req/s** | **94 ms** | 0% | ✅ p95<300 |
| Ingest (Kafka path) | 50 VUs, 25s | ~13 req/s | 4.9 s | 76% | ❌ (see caveat) |
| Login burst (abuse) | 50 req/s arrival | — | — | all 401/429 (expected) | ✅ |
| Mixed soak | 20w+30r VUs, 15 min | script provided | — | — | run on a real host |

**Reads vs writes.** The cache-aside read path sustains ~1.2k req/s at sub-100 ms p95. The write
path (each `POST /api/events` publishes to Kafka, then persists) is bound by Kafka+MySQL contention
on this shared 8 GB box; a single ingest in isolation completes in **~130 ms**. On a dedicated host
(or with `KAFKA_ENABLED=false` for the synchronous path), ingest throughput is far higher — rerun
`k6 run load-tests/ingest.js` there to record it.

**Cached vs uncached / sync vs Kafka.** Toggle `REDIS_ENABLED` (reads) and `KAFKA_ENABLED` (writes)
on the backend and re-run the matching scenario to produce the comparison on your hardware.

Raw k6 summaries: `docs/perf/*.json` (gitignored).
