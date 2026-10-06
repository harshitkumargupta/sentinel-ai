# Service Level Objectives (SLOs)

SentinelAI is a single-node, zero-cost capstone deployment; these SLOs are scoped accordingly and
are measured from the Prometheus metrics the backend exposes at `/actuator/prometheus`.

| SLO | Target | Metric / query | Window |
|-----|--------|----------------|--------|
| **Availability** | 99.0% | `avg_over_time(up{job="sentinel-backend"}[30d])` | 30d |
| **API latency (read)** | p95 < 300 ms | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{uri!~"/api/events"}[5m])) by (le))` | 5m |
| **API latency (ingest)** | p95 < 500 ms | p95 of `http_server_requests_seconds_bucket{uri="/api/events"}` | 5m |
| **Error rate** | < 1% 5xx | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` | 5m |
| **Detection latency** | p95 time-to-detect < 5 s | event → alert latency (evaluation harness) | per run |
| **Pipeline freshness** | consumer lag < 1000 | `sum(kafka_consumer_fetch_manager_records_lag)` | 5m |

## Error budget & burn rate

- **Availability**: 99.0%/30d ⇒ budget ≈ 7h 18m downtime/month. Alert on **fast burn** (2% of the
  monthly budget in 1h → `ServiceDown` for 1m is the proxy on a single node) and **slow burn**
  (error-rate alert sustained 5m, see `alert.rules.yml`).
- **Latency**: the p95 alert (`HighP95Latency`, >500 ms for 5m) is the budget-burn signal for the
  latency SLO.
- Single-node caveat: there is no redundancy, so a host restart consumes availability budget; the
  `restart: unless-stopped` policy + health-gated deploy minimise MTTR.

## Where to see it

- Grafana → *SentinelAI → Overview* (latency percentiles, error rate, JVM, DB pool, Kafka lag).
- Prometheus → Alerts tab shows the rules in `infrastructure/prometheus/alert.rules.yml`.

> Note: a few panels (detection events/sec, AI latency/faithfulness, DLQ/outbox gauges) depend on
> custom Micrometer meters; where a meter is not yet wired they render "no data". The core API/JVM/
> DB/Kafka panels populate from Spring Boot + Micrometer auto-instrumentation.
