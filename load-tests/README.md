# Load tests (k6)

Install k6 (`brew install k6`), run the backend (dev or prod), then:

```bash
BASE_URL=http://localhost:8080 ADMIN_USER=admin ADMIN_PASS=Admin@123 \
  k6 run load-tests/ingest.js          # ingest ramp (sync vs Kafka: toggle KAFKA_ENABLED)
k6 run load-tests/dashboard.js         # cached read mix (cached vs uncached: toggle Redis)
k6 run load-tests/login-burst.js       # abuse: lockout / rate limiting
k6 run load-tests/soak.js              # 15-min mixed soak
```

Each script declares pass/fail **thresholds** (`http_req_failed`, `http_req_duration` p95). Export a
summary with `k6 run --summary-export=docs/perf/<name>.json ...`. Record the hardware and the headline
numbers in [`docs/performance.md`](../docs/performance.md).
