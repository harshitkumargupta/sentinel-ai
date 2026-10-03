# 🛡️ SentinelAI

An AI-powered **mini SOC (Security Operations Center)** platform — final-year capstone project.

SentinelAI ingests security events, correlates them into incidents through a detection engine,
scores risk, and gives analysts a dashboard to investigate — with AI assistance for triage and
summarization in later phases.

> **Status:** Phase 0 & Phase 1 (scaffold + runnable skeleton). Kafka, Redis, AI, and AWS are
> intentionally **not** included yet.

---

## Tech stack

| Layer            | Technology |
|------------------|------------|
| Frontend         | React, Vite, React Router, Axios |
| Backend          | Java 21, Spring Boot 3 (Web, Security, Data JPA, Validation, Actuator), Flyway, Lombok, springdoc-openapi |
| Database         | MySQL 8 (via Docker Compose) |
| Build & CI       | Maven, npm, GitHub Actions |
| Observability    | Actuator + Prometheus endpoint (Grafana later) |
| Planned (later)  | Redis, Kafka, AI services, Prometheus/Grafana, AWS, Kubernetes |

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

Under the `dev` profile, `DevDataSeeder` inserts one user per role (passwords BCrypt-hashed) and
three sample detection rules on first startup (idempotent). Dev login credentials:

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

Create it once:

```sql
CREATE DATABASE sentinelai_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON sentinelai_test.* TO 'sentinel'@'localhost';
```

> **TODO (once Docker is available):** migrate these tests to **Testcontainers** so each run
> spins up an ephemeral MySQL container and no local `sentinelai_test` database is required.

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
- [ ] **Phase 3** — Authentication & authorization (JWT, roles ADMIN/ANALYST/VIEWER)
- [ ] **Phase 4** — Event ingestion API
- [ ] **Phase 5** — Incident model & triage workflow
- [ ] **Phase 6** — Detection rule engine
- [ ] **Phase 7** — Risk scoring
- [ ] **Phase 8** — Dashboard read models & metrics API
- [ ] **Phase 9** — Frontend: events & incidents views
- [ ] **Phase 10** — Frontend: dashboard & charts
- [ ] **Phase 11** — Redis (caching, rate limiting)
- [ ] **Phase 12** — Kafka (event streaming pipeline)
- [ ] **Phase 13** — AI: incident summarization & triage assistance
- [ ] **Phase 14** — AI: natural-language querying
- [ ] **Phase 15** — Observability (Prometheus/Grafana dashboards)
- [ ] **Phase 16** — Hardening & security review
- [ ] **Phase 17** — Dockerize full stack
- [ ] **Phase 18** — Kubernetes manifests
- [ ] **Phase 19** — AWS deployment & CI/CD to cloud

---

## Documentation

- [Architecture](docs/architecture.md)
- [Event taxonomy & roles](docs/event-taxonomy.md)
- [API contracts](docs/api-contracts.md)
- [Entity-relationship diagram (ERD)](docs/erd.md)
