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
