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

   Now present: Kafka (event-driven pipeline), Redis (cache/rate-limit/baselines).
   Later phases add: Prometheus/Grafana (observability), AWS deployment.
```

## 2a. Event-driven pipeline (Kafka)

When `sentinel.kafka.enabled=true`, ingestion becomes asynchronous: the REST endpoint persists the
event and hands detection off to Kafka, returning `202 Accepted`. When it is `false` (or the broker
is unreachable), the same code path falls back to the in-process synchronous `EventProcessor` with
no behavioural change for callers. Messages are **keyed by `entity_key`** so one entity's events
stay ordered on a single partition.

```mermaid
flowchart LR
    subgraph ingress
      REST["POST /api/events/ingest"]
      RAW[("events.raw")]
    end
    REST -->|persist event + outbox row\n(one DB tx)| OUTBOX[("outbox table")]
    RAW -->|RawIngestConsumer| OUTBOX
    OUTBOX -->|OutboxRelay (scheduled)| NORM[("events.normalized")]

    NORM -->|DetectionConsumer\nrules| ALERTS[("alerts")]
    NORM -->|AnalyticsConsumer\ncounters + baselines| METRICS["Micrometer / baselines"]
    ALERTS -->|CorrelationConsumer\ncorrelate → incident| INC[("incidents.updates")]
    INC -->|NotificationConsumer\nHIGH/CRITICAL| NOTIF["notifications"]

    NORM -.->|handler throws| RETRY[("events.retry")]
    RETRY -->|exp backoff, re-dispatch| NORM
    RETRY -->|attempts exhausted| DLQ[("events.dlq")]
    DLQ -->|DlqConsumer| DLQT[("dlq_messages table")]
    DLQT -->|admin replay| NORM

    classDef topic fill:#eef,stroke:#88a;
    class RAW,NORM,ALERTS,INC,RETRY,DLQ topic;
```

Reliability properties:

- **Outbox pattern** — the event row and its outbox row commit in one DB transaction; a scheduled
  relay publishes pending rows, so nothing is lost if the app crashes between the DB write and the
  publish. The relay uses the outbox row id as the message id, so a duplicate publish after an ack
  timeout deduplicates downstream.
- **Idempotency** — each consumer records `(consumer_group, message_id)` in `processed_messages` in
  the same transaction as its side effect; a duplicate delivery is a no-op.
- **Retry + DLQ** — a failed message is routed to `events.retry` with an incremented attempt,
  retried with exponential backoff on a single-threaded retry consumer (so backoff never blocks a
  main partition), and dead-lettered to `events.dlq` once attempts are exhausted. The DLQ is
  persisted to `dlq_messages` for inspection and replay.
- **Manual offset commits** — a listener commits its offset only after the DB transaction succeeds
  (or the poison message has been moved aside), so a crash re-delivers rather than drops.

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
