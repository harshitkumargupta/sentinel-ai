# 🛡️ SentinelAI

An AI-powered **mini SOC (Security Operations Center)** platform — final-year capstone project.

SentinelAI ingests security events, correlates them into incidents through a detection engine,
scores risk, and gives analysts a dashboard to investigate — with AI assistance for triage and
summarization in later phases.

> **Status:** Phase 12 — AI investigation pipeline: evidence-validated incident analysis, prompt-
> injection defense, and safe natural-language search. AI is optional and behind `ai.enabled`; with
> it off (or the model unavailable/over budget) every AI feature returns a deterministic fallback.
> Kafka and Redis remain optional with their own fallbacks. AWS is still intentionally **not** in yet.

---

## Tech stack

| Layer            | Technology |
|------------------|------------|
| Frontend         | React, Vite, React Router, Axios |
| Backend          | Java 21, Spring Boot 3 (Web, Security, Data JPA, Validation, Actuator), Flyway, Lombok, springdoc-openapi |
| Database         | MySQL 8 (via Docker Compose) |
| Messaging        | Kafka (KRaft, no ZooKeeper) — event-driven pipeline with outbox, idempotency, retry + DLQ; optional, syncs fall back |
| Build & CI       | Maven, npm, GitHub Actions |
| Observability    | Actuator + Prometheus endpoint (Grafana later) |
| Caching / limits | Redis (Lettuce) — cache-aside, token-bucket rate limiting, sliding-window store; in-memory fallback |
| AI               | Pluggable LLM (OpenAI-compatible HTTP or a deterministic fake) — evidence-validated investigation, injection defense, safe NL search; key only from `LLM_API_KEY` |
| Planned (later)  | Prometheus/Grafana, AWS, Kubernetes |

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
│   ├── docker/               # docker-compose.yml (MySQL, Redis, Kafka; Kafka UI via --profile ui)
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
- Docker — runs MySQL, Redis and Kafka (or bring your own on the default ports)

### 1. Start the infrastructure

```bash
docker compose -f infrastructure/docker/docker-compose.yml up -d
# optional Kafka UI at http://localhost:8085:
docker compose -f infrastructure/docker/docker-compose.yml --profile ui up -d
```

This starts **MySQL** (`sentinelai`, user `sentinel`/`sentinel`), **Redis** (`:6379`) and **Kafka**
(KRaft, `:9092`) — all with health checks, matching the backend `dev` profile defaults. Override
with `DB_URL`/`DB_USER`/`DB_PASSWORD`, `REDIS_HOST`/`REDIS_PORT`, and `KAFKA_BOOTSTRAP_SERVERS`.

On the `dev` profile the Kafka pipeline is **on** (`KAFKA_ENABLED`, default `true`); set
`KAFKA_ENABLED=false` to force the synchronous ingestion path. Redis is optional (in-memory
fallback). If Kafka is unreachable, ingestion degrades gracefully to the synchronous path.

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

Tests run against **real infrastructure via [Testcontainers](https://testcontainers.org)** — only
Docker is required, no local test database or brokers. MySQL is provided by the Testcontainers JDBC
URL (`jdbc:tc:mysql:8.4:///…` in `src/test/resources/application-test.yml`), so `@DataJpaTest` and
full `@SpringBootTest` runs exercise native types (ENUM, JSON) and Flyway migrations exactly as in
production. The Redis parity test and the Kafka pipeline tests start ephemeral Redis / Kafka
containers on demand (`support.Containers`).

The **Kafka pipeline tests** (`KafkaPipelineTest`, `KafkaDownFallbackTest`) cover the happy path end
to end, idempotent duplicate delivery, retry→DLQ on a poison message, a poison message not blocking
its partition, outbox recovery after a simulated crash, Kafka-down → synchronous fallback, DLQ
replay, per-key ordering, and consumer-restart offset resume.

> On macOS with Colima instead of Docker Desktop, point Testcontainers at the Colima socket:
> `export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"` (and
> `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`).

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
- [x] **Phase 11** — Kafka event-driven pipeline (outbox, idempotency, retry + DLQ, chaos demo)
- [x] **Phase 12** — AI investigation (evidence validator, injection defense, safe NL search)
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

### AI: evidence-validated investigation (Phase 12)

Behind `sentinel.ai.enabled` (dev default on, with a deterministic **fake** provider so no key is
needed). The API key for a real model comes **only** from `LLM_API_KEY` (never committed or logged);
set `AI_PROVIDER=http` and `AI_MODEL` to use an OpenAI-compatible endpoint. With AI off, or the model
unavailable/over budget, every feature returns a deterministic fallback — the core API never fails
because of the LLM.

- **Investigate** — `POST /api/incidents/{id}/investigate` (ANALYST+, rate-limited, idempotent) runs
  the 3-stage pipeline (analyze → correlate → recommend) asynchronously (Kafka or a thread pool) and
  returns `202` with an analysis id. `GET /api/incidents/{id}/analysis` and `GET /api/analysis/{id}`
  read results; `POST /api/analysis/{id}/review {APPROVE|REJECT|MODIFY}` is audit-logged and approved
  recommendations become **PROPOSED** playbook actions.
- **Evidence validator** (the key control) — every claim must cite event ids from *this* incident;
  recommendation actions are allow-listed and targets must appear in the evidence; a faithfulness
  score is recorded; invalid output gets one repair retry, then a deterministic FALLBACK.
- **Prompt-injection defense** — untrusted log data is delimited and the model told it is not
  instructions; an `InjectionDetector` raises a `PROMPT_INJECTION` event and flags the incident; the
  model has no tools and output is schema-validated regardless of what the data says.
- **Safe NL search** — `POST /api/search/nl {query}` → the model returns an allow-listed filter only
  (never SQL); it is validated, capped, and run through the existing parameterized query, with the
  interpreted filter shown as chips. `GET /api/evaluation/ai` reports faithfulness/validity/injection
  metrics.

See [`docs/ai-design.md`](docs/ai-design.md), [`docs/adr/ADR-002-ai-guardrails.md`](docs/adr/ADR-002-ai-guardrails.md)
and [`docs/ai-evaluation.md`](docs/ai-evaluation.md).

### Kafka: event-driven ingestion pipeline

Behind `sentinel.kafka.enabled` (dev default on), `POST /api/events/ingest` persists the event and
an **outbox** row in one transaction and returns `202 Accepted`; a scheduled relay publishes outbox
rows to Kafka, and independent consumer groups carry the event through detection, correlation,
analytics and notification. With the flag off — or the broker unreachable — the same endpoint runs
the original synchronous in-process detection path (returning `200`). Messages are keyed by
`entity_key` so one entity's events stay ordered per partition. See
[`docs/adr/ADR-001-kafka.md`](docs/adr/ADR-001-kafka.md) and the flow diagram in
[`docs/architecture.md`](docs/architecture.md).

**Topic map**

| Topic | Produced by | Consumed by (group) | Purpose |
|-------|-------------|---------------------|---------|
| `events.raw` | external collectors / chaos burst | raw-ingest (`sentinel-raw-ingest`) | normalize + persist (→ outbox) |
| `events.normalized` | outbox relay | detection (`sentinel-detection`), analytics (`sentinel-analytics`) | run rules; counters + baselines |
| `alerts` | detection | correlation (`sentinel-correlation`) | correlate alerts into incidents |
| `incidents.updates` | correlation | notification (`sentinel-notification`) | notify on HIGH/CRITICAL |
| `events.retry` | any failing consumer | retry (`sentinel-retry`) | exponential-backoff re-dispatch |
| `events.dlq` | retry (exhausted) | dlq (`sentinel-dlq`) | persist dead letters for replay |

Reliability: **outbox** (no loss across crashes), **idempotency** via `processed_messages`
(duplicate delivery is a no-op), **retry→DLQ** with manual offset commits (poison never blocks a
partition). Admin surface: `GET /api/admin/pipeline-status`, `GET /api/admin/dlq` +
`POST /api/admin/dlq/{id}/replay` + `/replay-all`, and chaos hooks
`POST /api/admin/chaos/{pause,resume,burst}` (all ADMIN-only, audit-logged). Metrics (publish/consume
counts, consumer lag, processing time, retry/DLQ counts, outbox backlog) are exposed via Actuator.
The React **Pipeline** page (ADMIN) shows the status card, DLQ table and chaos controls.

**Chaos demo & benchmark** (backend up with Kafka enabled):

```bash
# pause a consumer, burst N events, watch lag, resume, verify zero loss / zero duplicates
ADMIN_USER=admin ADMIN_PASS='Admin@123' scripts/chaos-demo.sh 2000

# compare sync vs Kafka ingest (run per mode; see docs/performance.md for numbers)
MODE=kafka N=10000 scripts/benchmark.sh
```

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
