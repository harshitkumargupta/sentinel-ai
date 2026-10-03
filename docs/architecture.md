# SentinelAI — Architecture

## 1. Overview

SentinelAI is an AI-powered **mini SOC (Security Operations Center)** platform. It ingests
security events, correlates them into incidents via a detection engine, scores risk, and
presents everything to analysts through a dashboard — with AI assistance for triage and
summarization in later phases.

The backend is a **modular monolith**: a single deployable Spring Boot application split into
clear feature modules that communicate through interfaces. This keeps early development simple
while leaving a clean seam to extract services (e.g. ingestion, detection) later.

## 2. High-level diagram

```
                         ┌─────────────────────────┐
                         │     Frontend (React)     │
                         │  Vite · React Router ·   │
                         │        Axios             │
                         └───────────┬─────────────┘
                                     │ HTTPS / REST (/api)
                                     ▼
            ┌────────────────────────────────────────────────┐
            │          Backend — Spring Boot (Java 21)        │
            │                modular monolith                 │
            │                                                 │
            │  auth │ event │ incident │ detection │ risk     │
            │       │       │          │           │          │
            │  ai   │ dashboard │ audit │     common          │
            └───────────────────────┬────────────────────────┘
                                     │ JPA / Flyway
                                     ▼
                         ┌─────────────────────────┐
                         │          MySQL           │
                         └─────────────────────────┘

   Later phases add: Kafka (event streaming), Redis (cache/rate-limit),
   AI services, Prometheus/Grafana (observability), AWS deployment.
```

## 3. Modules (packages under `com.sentinelai`)

| Module       | Responsibility |
|--------------|----------------|
| `auth`       | Users, roles (ADMIN/ANALYST/VIEWER), login, JWT issuance. |
| `event`      | Ingest and store normalized security events. |
| `incident`   | Group events into incidents; triage lifecycle. |
| `detection`  | Rule engine that evaluates events and raises incidents. |
| `risk`       | Risk scoring per user / IP / asset. |
| `ai`         | AI-assisted summarization, triage suggestions, NL query. |
| `dashboard`  | Aggregated read models for the SOC overview. |
| `audit`      | Immutable audit trail of platform actions. |
| `common`     | Cross-cutting: security, OpenAPI, errors, base entities. |

## 4. Technology by layer

- **Frontend:** React + Vite, React Router, Axios.
- **Backend:** Java 21, Spring Boot 3 (Web, Security, Data JPA, Validation, Actuator),
  Flyway migrations, springdoc-openapi, Lombok.
- **Data:** MySQL (via Docker Compose locally).
- **Build/CI:** Maven, npm, GitHub Actions.
- **Later:** Redis, Kafka, AI, Prometheus/Grafana, AWS, Kubernetes.

## 5. Environments & profiles

- `dev` — local development; connects to Dockerized (or local) MySQL, verbose logging,
  `ddl-auto: validate` with Flyway owning the schema.
- `prod` — credentials and URLs injected via environment/secrets; minimal logging; tuned
  connection pool.

## 6. Conventions

- Schema changes go through **Flyway** migrations only (`db/migration/V*.sql`).
- Each module owns its persistence; cross-module access goes through service interfaces.
- The public health contract is `GET /api/health`; infra probes use `/actuator/health`.
