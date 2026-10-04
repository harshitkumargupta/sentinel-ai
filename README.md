# 🛡️ SentinelAI

An AI-powered **mini SOC (Security Operations Center)** platform — final-year capstone project.

SentinelAI ingests security events, correlates them into incidents through a detection engine,
scores risk, and gives analysts a dashboard to investigate — with AI assistance for triage and
summarization in later phases.

> **Status:** Phase 10 — Redis (caching, rate limiting, sliding windows), behavioral baselines,
> and the attack-storyline graph. Redis is optional: every use degrades gracefully to an in-memory
> fallback. Kafka and AWS are still intentionally **not** included yet.

---

## Tech stack

| Layer            | Technology |
|------------------|------------|
| Frontend         | React, Vite, React Router, Axios |
| Backend          | Java 21, Spring Boot 3 (Web, Security, Data JPA, Validation, Actuator), Flyway, Lombok, springdoc-openapi |
| Database         | MySQL 8 (via Docker Compose) |
| Build & CI       | Maven, npm, GitHub Actions |
| Observability    | Actuator + Prometheus endpoint (Grafana later) |
| Caching / limits | Redis (Lettuce) — cache-aside, token-bucket rate limiting, sliding-window store; in-memory fallback |
| Planned (later)  | Kafka, Prometheus/Grafana, AWS, Kubernetes |

Architecture: the backend is a **modular monolith** under `com.sentinelai` with modules
`auth · event · incident · detection · risk · ai · dashboard · audit · common`.
See [`docs/architecture.md`](docs/architecture.md).

---

## Project structure

```
sentinel-ai/
├── frontend/                 # React app (Vite)
├── backend/                  # Spring Boot (Maven, Java 21)
├── infrastructure/
│   ├── docker/               # docker-compose.yml (MySQL for now)
│   └── k8s/                  # Kubernetes manifests (later)
├── .github/workflows/        # CI: backend build + frontend build
├── docs/                     # architecture, event taxonomy, API contracts
├── README.md
└── .gitignore
```

---

## Setup

### Prerequisites
- Java 21, Maven
- Node.js 20+ and npm
- Docker (for the MySQL container) — or a local MySQL on `:3306`
- **Redis** (optional) — `brew install redis && brew services start redis`. The `dev` profile
  enables Redis (`REDIS_ENABLED`); if it's absent the app logs the outage once and falls back to
  in-memory caching / rate limiting / windows, so this step can be skipped.

### 1. Start the database

```bash
docker compose -f infrastructure/docker/docker-compose.yml up -d
```

This starts MySQL with database `sentinelai` and user `sentinel` / `sentinel` (matching the
backend `dev` profile defaults). To point the backend at a different MySQL, set `DB_URL`,
`DB_USER`, and `DB_PASSWORD`.

### 2. Run the backend

```bash
cd backend
mvn spring-boot:run
```

- API: http://localhost:8080
- Health: http://localhost:8080/api/health
- Swagger UI: http://localhost:8080/swagger-ui.html

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

- App: http://localhost:5173 (the login page; it links through to the dashboard, which pings the backend health endpoint).

### Dev seed data & credentials

Under the `dev` profile, `DevDataSeeder` inserts one user per role (passwords BCrypt-hashed),
three sample detection rules (with MITRE technique IDs), and two SHA-256-hashed honeytokens on
first startup (idempotent, all under Default Org). Dev login credentials:

| Username  | Email                  | Password      | Role    |
|-----------|------------------------|---------------|---------|
| `admin`   | admin@sentinel.ai      | `Admin@123`   | ADMIN   |
| `analyst` | analyst@sentinel.ai    | `Analyst@123` | ANALYST |
| `viewer`  | viewer@sentinel.ai     | `Viewer@123`  | VIEWER  |

> These are **development-only** credentials for local use. They are not seeded under `prod`.

---

## Testing

```bash
cd backend
mvn verify
```

Repository tests (`@DataJpaTest`) run against a **real MySQL** database so native types (ENUM,
JSON) and Flyway migrations are exercised exactly as in production. They use a separate schema,
`sentinelai_test`, configured in `src/test/resources/application-test.yml` (defaults:
`sentinel` / `sentinel`; override with `TEST_DB_URL` / `TEST_DB_USER` / `TEST_DB_PASSWORD`).

Create it once (single command — you'll be prompted for the MySQL root password):

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS sentinelai_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL PRIVILEGES ON sentinelai_test.* TO 'sentinel'@'localhost'; FLUSH PRIVILEGES;"
```

The **Redis integration test** (`RedisWindowStoreParityTest`) asserts the Redis and in-memory
sliding-window stores make identical counting decisions. It requires a local Redis on
`localhost:6379` (`brew install redis && brew services start redis`). All other Redis behaviour
(rate limiter, cache, window-store fallback) is covered by unit tests that exercise the in-memory
path, so the suite passes even when Redis is down.

> **TODO (once Docker is available):** migrate these tests to **Testcontainers** so each run
> spins up ephemeral MySQL **and Redis** containers — no local `sentinelai_test` DB or Redis needed.

---

## Database

Schema is owned by **Flyway** migrations in `backend/src/main/resources/db/migration`
(`V1`…`V14`), applied automatically on startup; `spring.jpa.hibernate.ddl-auto=validate` makes
the JPA entities verify against the migrated schema (the app fails fast on drift). MySQL 8+
(InnoDB, `utf8mb4`).

- **Multi-tenant:** `organizations` is the tenancy root; every org-scoped table carries `org_id`.
  Migration `V2` seeds `Default Org` (id 1).
- **Enums** use native MySQL `ENUM` (uppercase); **JSON** columns hold structured payloads
  (`raw_payload`, `risk_breakdown`, `config`, `details`, …); audit hashes are `CHAR(64)`.
- **13 tables:** organizations, users, security_events, incidents, incident_events,
  detection_rules, backtest_runs, ai_analyses, audit_logs, notifications, honeytokens,
  entity_baselines, playbook_actions.
- **Delete rules:** CASCADE for owned links (`incident_events`, `backtest_runs`), SET NULL for
  optional user references, RESTRICT elsewhere. `audit_logs` is insert-only (hash-chained).
- Full diagram and index list: [`docs/erd.md`](docs/erd.md).

JPA entities and Spring Data repositories live in their module packages (`auth`, `event`,
`incident`, `detection`, `ai`, `audit`, `honeytoken`, `baseline`, `playbook`, `notification`),
with a shared `BaseAuditableEntity` and `Organization` in `common`.

---

## Branching strategy

- **`main`** — always releasable; protected. Only merges from `develop` or hotfix branches.
- **`develop`** — integration branch; features merge here first.
- **`feature/*`** — one branch per feature/task (e.g. `feature/auth-jwt`), branched from
  `develop` and merged back via pull request.
- Hotfixes: `hotfix/*` from `main`.

Commits follow [Conventional Commits](https://www.conventionalcommits.org/)
(`feat:`, `fix:`, `chore:`, `docs:`…).

---

## Roadmap — phase checklist

- [x] **Phase 0** — Project scaffold (repo structure, tooling, docs, CI)
- [x] **Phase 1** — Runnable skeleton (Spring Boot + MySQL, React shell, `/api/health`)
- [x] **Phase 2** — Database & domain model (events, incidents, rules, users, audit)
- [x] **Phase 3** — Authentication & authorization (JWT, roles ADMIN/ANALYST/VIEWER)
- [x] **Phase 4** — REST APIs (events, incidents, rules, users, dashboard) + audit hash chain
- [x] **Phase 5** — Incident model & triage workflow (status/feedback/assign APIs)
- [x] **Phase 6** — Detection rule engine (pluggable strategies, auto incident creation/correlation)
- [x] **Phase 7** — Risk scoring (factor pipeline, correlation, incidents, timeline)
- [x] **Phase 8** — Dashboard read models, metrics API, risk waterfall & incident UI
- [x] **Phase 8b** — Multi-site + admin-action risk engine (sites, API keys, admin risk guard)
- [x] **Phase 9** — ML risk model (anomaly models, FastAPI scoring service, hybrid risk)
- [x] **Phase 10** — Redis (caching, rate limiting, sliding windows), behavioral baselines, attack-storyline graph
- [ ] **Phase 11** — Kafka (event streaming pipeline)
- [ ] **Phase 12** — AI: incident summarization & triage assistance
- [ ] **Phase 13** — AI: natural-language querying
- [ ] **Phase 14** — Observability (Prometheus/Grafana dashboards)
- [ ] **Phase 15** — Hardening & security review
- [ ] **Phase 16** — Dockerize full stack
- [ ] **Phase 17** — Kubernetes manifests
- [ ] **Phase 18** — AWS deployment & CI/CD to cloud

---

## API & authentication

Stateless **JWT** auth. Log in to get a 15-minute access token and a 7-day refresh token
(the refresh token is stored only as a SHA-256 hash). Send the access token as
`Authorization: Bearer <token>`; the Axios client refreshes it automatically on a 401.
Accounts lock for 15 minutes after 5 failed logins. Every login failure and admin action is
also recorded as a `security_event` (self-monitoring) and in the tamper-evident audit chain.
Set `JWT_SECRET` (≥ 32 chars) in every non-dev environment.

Interactive docs with a "Bearer" auth button: `/swagger-ui.html`.

### Roles

| Capability | VIEWER | ANALYST | ADMIN |
|---|:---:|:---:|:---:|
| Read events / incidents / rules / dashboard | ✓ | ✓ | ✓ |
| Ingest events (`POST /api/events`) | | ✓ | ✓ |
| Change incident status / feedback / assignee | | ✓ | ✓ |
| Manage detection rules (CRUD, enable/disable) | | | ✓ |
| Manage users; read/verify audit logs | | | ✓ |

### Endpoints

| Method | Path | Access |
|---|---|---|
| POST | `/api/auth/login` · `/api/auth/refresh` | public |
| POST | `/api/auth/logout` · GET `/api/auth/me` | authenticated |
| GET/POST/PUT/PATCH/DELETE | `/api/users`, `/api/users/{id}`, `/api/users/{id}/disable` | ADMIN |
| GET | `/api/events` · `/api/events/{id}` | VIEWER+ |
| POST | `/api/events` | ANALYST+ |
| GET | `/api/incidents` · `/api/incidents/{id}` | VIEWER+ |
| PATCH | `/api/incidents/{id}/status` · `/feedback` · `/assign` | ANALYST+ |
| GET | `/api/rules` · `/api/rules/{id}` | VIEWER+ |
| POST/PUT/DELETE/PATCH | `/api/rules`, `/api/rules/{id}`, `/api/rules/{id}/enabled` | ADMIN |
| GET | `/api/dashboard/summary` | VIEWER+ |
| GET | `/api/audit-logs` (paged) · `/api/audit-logs/verify` | ADMIN |
| POST | `/api/events/ingest` · `/api/events/ingest/batch` | ANALYST+ |
| GET | `/api/alerts` (paged) | VIEWER+ |
| POST | `/api/rules/{id}/backtest` | ADMIN |
| POST | `/api/simulator/run` · GET `/api/simulator/runs` | ADMIN (flag on) |
| GET | `/api/evaluation/detection?runId=` | VIEWER+ |
| GET | `/api/incidents/{id}/timeline` · `/risk` · `/evidence` | VIEWER+ |
| GET | `/api/dashboard/alert-reduction` · `/mitre-coverage` | VIEWER+ |
| GET/POST | `/api/sites`, `/api/sites/{id}/keys/rotate`, `/api/sites/{id}/snippet` | ADMIN (list: any) |
| POST | `/api/events/ingest` with `X-API-Key` | per-site ingest key |
| POST | `/api/admin/actions/disable-user/{id}` (risk-gated) | ADMIN |
| GET/POST | `/api/admin/pending[/{id}/approve|reject]` | ADMIN |
| GET/POST | `/api/admin/sessions/{userId}[/revoke]` · `/api/admin/timeline` | ADMIN |
| GET | `/api/incidents/{id}/graph` (attack-storyline graph) | VIEWER+ |
| GET | `/api/admin/cache-stats` (cache hit/miss/hit-rate) | ADMIN |

### ML risk model (hybrid)

A Python **ML scoring service** (`ml/`, FastAPI + scikit-learn + SHAP) trains IsolationForest +
GradientBoosting models and serves `POST /score` (features → `{score, model_version, top_features}`).
The Java `MlScoringClient` (resilient HTTP impl with timeout/retry/circuit breaker, NoOp fallback,
behind `ml.enabled`) feeds an **`MlRiskFactor`** into the risk pipeline with a **capped** weight — the
model augments but never overrides the hard rules — and the SHAP reasons + model version appear in the
incident waterfall. The same factor plugs into the admin-risk engine; a scheduled **drift monitor**
raises an alert when live feature stats diverge from training. Shared feature spec:
[docs/ml-features.md](docs/ml-features.md). Rules-only vs model vs hybrid:
[docs/ml-evaluation.md](docs/ml-evaluation.md) — on held-out data rules-only recall ≈ 0.65 vs
model/hybrid ≈ 1.00. Run the service: `cd ml && uvicorn app:app --port 8000` (see [ml/README.md](ml/README.md)).

### Multi-site & admin-action risk

An org has multiple **sites**; events/incidents/rules are site-tagged and ingestion authenticates
with a per-site **`X-API-Key`** (hashed, rotatable/revocable). Sensitive **admin actions** run
through a risk-adaptive **guard** (factors: time, new IP/country/device, action sensitivity, burst,
privilege escalation, peer deviation, unusual site): LOW allow · MEDIUM step-up · HIGH pending
approval by another admin (no self-approval, expires) · CRITICAL block + session revoke + notify.
See [docs/admin-risk.md](docs/admin-risk.md).

### Risk, correlation & incidents

Detection **alerts** are correlated into **incidents** by entity (user/IP) within a time window
(chaining related rule types). Each incident is risk-scored (0–100) by a pluggable
`RiskFactor` pipeline (severity, frequency, repetition, asset criticality, honeytoken, user
behavior, MITRE kill-chain stage) with weights/cutoffs in `sentinel.risk.*` — see
[docs/risk-model.md](docs/risk-model.md). Joining is idempotent and rescores on every alert;
crossing into HIGH/CRITICAL escalates and notifies admins. State machine
`OPEN → INVESTIGATING → CONTAINED → RESOLVED` (+ `FALSE_POSITIVE`), with a full
`incident_timeline`. Reference run (seed 42): **174 events → 33 alerts → 10 incidents
(94% reduction)**, incident-level precision/recall 1.0.

### Detection, ingestion & simulation

Ingested events (API or simulator) are normalized, GeoIP-enriched, deduped by optional
`clientEventId`, then run **synchronously** through pluggable detection rules that emit **alerts**.
Rule thresholds live in each rule's `config` JSON (admin-editable, no redeploy), e.g.
`{"threshold":10,"windowSeconds":300,"groupBy":"username"}`. Rules: `BRUTE_FORCE`,
`CREDENTIAL_STUFFING`, `HIGH_FREQUENCY_API`, `SUSPICIOUS_LOGIN`, `IMPOSSIBLE_TRAVEL`,
`ABNORMAL_ACCESS`, `HONEYTOKEN` — see [docs/detection-rules.md](docs/detection-rules.md).

The **simulator** (`sentinel.simulator.enabled=true`, dev default on) generates deterministic,
labeled events for scenarios `normal, brute_force, credential_stuffing, suspicious_login,
impossible_travel, api_abuse, abnormal_access, honeytoken`, and the **evaluation harness** scores
detection against those labels (precision/recall/F1, mean latency). Reference run (seed 42): overall
precision ≈ 0.99, recall 1.00.

### Redis: caching, rate limiting & resilient fallback

Redis (Lettuce) backs three things, each behind an interface with an **in-memory fallback** so the
app never fails a request when Redis is disabled or down (`sentinel.redis.enabled`, default off; dev
on). A single `RedisGateway` seam catches failures, logs the outage **once** with a traceId, counts
it under the `sentinel.redis.failures` meter, and degrades:

- **Cache-aside** for dashboard reads (`summary`, `alert-reduction`, `mitre-coverage`,
  `recent-incidents`) with a short TTL, a per-key single-flight **stampede guard**, **tenant-scoped
  keys** (`dash:org:{org}:site:{site}:{name}`) and **explicit event-driven invalidation** when
  incidents/alerts change. Stats at `GET /api/admin/cache-stats`. Benchmark (cached vs uncached
  p50/p95): [docs/performance.md](docs/performance.md) — **p95 13.9 ms → 3.2 ms (~4.3×)**.
- **Token-bucket rate limiting** (atomic Lua) per API-key/IP, configurable per endpoint group
  (`sentinel.rate-limit.*`: login strict, ingest generous). Breaches return `429` in the unified
  error format with `Retry-After` + `X-RateLimit-*` headers and are recorded as `API_ABUSE` events.
- **Sliding-window store** for detection rules (sorted sets); `RedisWindowStore` and the in-memory
  store make **identical** counting decisions (parity test).

### Behavioral baselines

Per-entity (user/IP/admin) × metric (e.g. login hour) rolling **mean/std** computed incrementally
with **Welford's algorithm** — hot in Redis, periodically persisted to `entity_baselines`, with a
minimum sample count before a baseline is trusted (`sentinel.baseline.*`). A `BaselineDeviationRule`
flags `|z-score|` above threshold with a human-readable reason, a `BaselineFactor` feeds the risk
engine, and the same z-scores become ML features. **Cold-start entities are skipped, not flagged.**

### Attack-storyline graph

`GET /api/incidents/{id}/graph` returns typed **nodes** (incident, alert, user, IP, resource,
honeytoken) and **edges** (`HAS_ALERT`, `INVOLVES`, `LOGIN_FROM`, `ACCESSED`, `TOUCHED`) with counts
and timestamps, plus a **kill-chain** derived from each alert's MITRE technique. Node count is capped
(`sentinel.graph.max-nodes`) with the remainder aggregated. The incident page renders it as an
interactive SVG graph with click-to-inspect, a kill-chain strip, and a time slider that replays the
attack. Example (credential stuffing): 8 users → one source IP, kill-chain stage 3 (Credential
Access, T1110.004).

All responses use the `ApiResponse` envelope `{ success, data, error, timestamp }`.

---

## Documentation

- [Architecture](docs/architecture.md)
- [Event taxonomy & roles](docs/event-taxonomy.md)
- [API contracts](docs/api-contracts.md)
- [Entity-relationship diagram (ERD)](docs/erd.md)
- [Detection engine](docs/detection.md)
- [Detection rules & simulator](docs/detection-rules.md)
- [Risk model & correlation](docs/risk-model.md)
- [Multi-site & admin-action risk](docs/admin-risk.md)
- [ML feature spec](docs/ml-features.md) · [ML evaluation](docs/ml-evaluation.md) · [ML service](ml/README.md)
- [Caching benchmark (cached vs uncached)](docs/performance.md)
