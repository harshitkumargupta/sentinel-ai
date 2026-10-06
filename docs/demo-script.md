# 8-Minute Demo Script

A timed walkthrough that shows the whole platform end to end. Have the dev stack running (infra +
backend + frontend) and, for the last beat, the monitoring stack. Log in as `admin / Admin@123`.

| Time | Beat | Do this | Shows |
|------|------|---------|-------|
| 0:00 | **Context** (30s) | Open the dashboard. | Command center: threat level, KPIs, live 3D attack globe, charts, MITRE heatmap. |
| 0:30 | **Simulate brute force** (60s) | Admin → Simulator → run `brute_force` (or `POST /api/simulator/run`). | Events stream into the **live feed**; severity badges update; counters animate. |
| 1:30 | **One incident** (45s) | Open the newly-raised incident. | Correlation: many alerts → one incident; attack storyline graph (toggle **3D**); risk waterfall. |
| 2:15 | **AI summary + evidence** (60s) | Click **Investigate**. | Evidence-validated AI summary; each claim cites evidence; faithfulness score; fallback if no LLM. |
| 3:15 | **Injection blocked** (45s) | Submit an event / NL search with a prompt-injection payload. | The sanitizer + evidence validator refuse it; decision stays deterministic. |
| 4:00 | **Approve block w/ dry-run** (75s) | Actions panel → propose **Block IP** → **Dry-run** (blast radius) → **Approve** (two-person for HIGH) → **Execute**. | SOAR: human-approved response; protected-target + allow-list guards; mock firewall adapter. |
| 5:15 | **Rollback** (30s) | Click **Rollback** on the executed action. | Reversible response; before/after restored; timeline + audit entries. |
| 5:45 | **Audit verify + tamper test** (45s) | `GET /api/audit-logs/verify` → OK. Then hand-edit a row in MySQL and re-verify. | Tamper-evident hash chain detects the edit (names the broken link). |
| 6:30 | **Chaos kill/recover** (45s) | Pause a Kafka consumer / `docker kill` a consumer, publish a burst, resume. | Retry → DLQ → replay with **zero loss / zero duplicates** (idempotency + outbox). |
| 7:15 | **Grafana** (45s) | Open Grafana → *SentinelAI → Overview*. | Live request rate, p50/p95/p99 latency, JVM, DB pool, Kafka lag; alert rules. |
| 8:00 | **Wrap** | — | Zero-cost, Compose-based, security-hardened, observable. |

## Backup plan (network / LLM down)

- **No LLM / no internet**: set `AI_ENABLED=true AI_PROVIDER=fake` (or `AI_ENABLED=false`). The
  `FakeLlmClient` returns deterministic, evidence-valid summaries — the AI beat still works offline.
- **No Docker / infra**: run the backend against local MySQL with `KAFKA_ENABLED=false`
  `REDIS_ENABLED=false` (synchronous ingest, in-memory fallback). The chaos/Grafana beats are
  skipped; everything else works.
- **Pre-seeded run**: `./scripts/run-evaluation.sh` produces `docs/final-evaluation.md` with the
  numbers if the live simulation misbehaves.
- **Screenshots**: `docs/screenshots/` has dashboard (dark/light), command palette and Grafana
  captures as a fallback for any beat.
