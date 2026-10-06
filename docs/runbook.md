# Operations Runbook

Operational procedures for running SentinelAI. Commands assume the repo root. Docker Compose v2
(`docker compose`) or the standalone `docker-compose` both work.

## Start / stop

| Action | Command |
|--------|---------|
| Dev infra (MySQL/Redis/Kafka) | `docker compose -f infrastructure/docker/docker-compose.yml up -d` |
| Backend (dev) | `cd backend && JAVA_HOME=<JDK21> mvn spring-boot:run` |
| Frontend (dev) | `cd frontend && npm run dev` |
| Prod stack | `./scripts/deploy.sh <tag>` (health-gated + smoke + auto-rollback) |
| Observability | `docker compose -f infrastructure/docker/monitoring.yml up -d` → Grafana :3000 |
| Stop infra | `docker compose -f infrastructure/docker/docker-compose.yml down` |
| Stop prod | `docker compose -f infrastructure/docker/docker-compose.prod.yml down` |

## Backup & restore (MySQL)

```bash
./scripts/backup.sh                                  # timestamped gz dump, retention N days
./scripts/restore.sh backups/sentinelai-<ts>.sql.gz  # restore; then restart backend
```
Schedule `backup.sh` via cron. Restore is round-trip tested (write → backup → delete → restore).

## Deploy & rollback

```bash
./scripts/deploy.sh <tag>        # pull, up, health-gate, smoke test; auto-rollback on failure
./scripts/rollback.sh <prev-tag> # manual rollback; last good tag in infrastructure/docker/.deploy-state
```

## DLQ replay (Kafka)

When messages land in the dead-letter queue (see the pipeline card / `DlqGrowing` alert):
```bash
# As ADMIN:
curl -X POST $API/api/admin/dlq/{id}/replay   -H "Authorization: Bearer $T"   # one message
curl -X POST $API/api/admin/dlq/replay-all    -H "Authorization: Bearer $T"   # all
```
Processing is idempotent (idempotency keys), so replay cannot create duplicates. Inspect with
`GET /api/admin/dlq`.

## Key rotation

- **JWT_SECRET**: set a new ≥32-char secret in the environment and restart the backend. In-flight
  access tokens become invalid (≤15 min TTL); clients re-authenticate via refresh. Rotating also
  invalidates existing refresh tokens on next use → users log in again.
- **Per-site API keys**: `POST /api/sites/{id}/keys/rotate` (ADMIN). The old key is revoked; update
  the ingest client with the new key from the response.
- **DB_PASSWORD / LLM_API_KEY**: update the env/secret store and restart; prod fails fast if missing.

## Incident response for the platform itself

1. **Service down** (`ServiceDown` alert): check `docker compose ... ps` and container logs; the
   `restart: unless-stopped` policy auto-recovers crashes. If wedged, `deploy.sh` re-reconciles.
2. **High error rate / p95** (alerts): check Grafana → Overview; identify the hot endpoint; inspect
   backend logs by `traceId` (every log line carries the MDC trace id, also returned as
   `X-Trace-Id`).
3. **Auth anomalies**: failed logins and admin actions are recorded as `security_event`s and in the
   tamper-evident audit chain — verify integrity with `GET /api/audit-logs/verify` (ADMIN).
4. **Suspected tampering**: `audit-logs/verify` returns the first broken link; the hash chain makes
   silent edits detectable.
5. **AI/LLM outage**: AI degrades to the deterministic `FakeLlmClient`/fallbacks automatically
   (`ai.enabled` / circuit breaker / budget); no action needed to keep the core API up.

## Health & metrics

- Liveness/readiness: `GET /api/health`, `GET /actuator/health`.
- Metrics: `GET /actuator/prometheus` (scraped by Prometheus); dashboards in Grafana → *SentinelAI*.
- SLOs and alert rules: [`slo.md`](slo.md), `infrastructure/prometheus/alert.rules.yml`.
