# Viva — 40 Questions & Answers

Concise answers for a capstone defense. Grouped by theme.

## Architecture & design
1. **What is SentinelAI?** A zero-cost mini-SOC: ingests security events, correlates them into
   incidents via a pluggable detection engine, scores risk, adds evidence-validated AI triage and
   human-approved SOAR response, with a React command center and full observability.
2. **Overall architecture?** Spring Boot modular monolith (domain packages), MySQL (Flyway), Redis
   (cache/rate-limit), Kafka (event pipeline, optional), a Python ML service (FastAPI), a React/Vite
   SPA, behind Caddy; Prometheus/Grafana for metrics. All in Docker Compose.
3. **Why a modular monolith, not microservices?** Single-node, zero-cost target; module boundaries
   (detection, risk, incident, ai, playbook…) keep it decoupled without the ops cost of many
   services. It can be split later — the interfaces already exist.
4. **How are optional subsystems handled?** Feature flags (`ai.enabled`, `redis.enabled`,
   `kafka.enabled`, `honeytokens.enabled`); each degrades gracefully (sync ingest, in-memory cache,
   deterministic AI) so the core API never goes down with a dependency.
5. **How is the detection engine extensible?** Implement `DetectionRuleEvaluator` (a strategy) + a
   DB/JSON rule config — no engine edits. Rules: brute-force, impossible-travel, credential-stuffing,
   honeytoken, baseline-deviation, etc.
6. **How do events become incidents?** Rules emit alerts; alerts correlate by entity/time into
   incidents (dedup), building a timeline and an attack-storyline graph.

## Auth, RBAC & security
7. **Auth model?** Stateless JWT access tokens (15 min) + opaque refresh tokens (7 days, stored as
   SHA-256 hashes). `Authorization: Bearer`.
8. **How is JWT hardened?** HS256 pinned; required issuer + audience; exp/nbf with bounded clock
   skew; `alg:none`/tampered/wrong-key tokens rejected (tests in `JwtSecurityTest`).
9. **Refresh token security?** Rotation on every use; **reuse detection** — replaying a rotated
   token revokes the whole family (stolen-token mitigation).
10. **RBAC?** Roles VIEWER/ANALYST/ADMIN via method `@PreAuthorize`; plus an admin-risk guard and a
    two-person rule for HIGH/CRITICAL SOAR actions.
11. **How do you prevent IDOR / cross-tenant access?** Every by-id lookup filters on the actor's
    `org`; an automated `EndpointProtectionTest` enumerates all endpoints and fails the build if any
    is unintentionally public.
12. **OWASP Top 10 coverage?** See `docs/security/checklist.md` — headers/CSP, parameterized queries,
    secrets in env + gitleaks, SSRF guard on the LLM client, size/depth limits, etc.
13. **CSRF?** Not applicable — stateless bearer auth, no cookies/session (documented, AR-03).
14. **How are secrets managed?** Env-only; gitleaks (pre-commit + CI); prod fails fast on missing/
    weak `JWT_SECRET`/`DB_PASSWORD`/`LLM_API_KEY`.
15. **Account protection?** Lockout after N failed logins, generic errors (no user enumeration),
    every failure recorded as a self-monitoring `security_event` + audit entry.
16. **Audit integrity?** Hash-chained audit log; `GET /api/audit-logs/verify` detects any tamper and
    names the first broken link.

## Kafka, idempotency & resilience
17. **Why Kafka?** Decouple ingest from processing for burst absorption; optional (sync fallback).
18. **Exactly-once-ish semantics?** Transactional **outbox** for publishing + **idempotency keys**
    on consume (dedup table) → no loss, no duplicates on retry/replay.
19. **DLQ?** Failed messages after retries go to a dead-letter queue; admin can replay (idempotent).
20. **Chaos test?** Pause consumers / kill, publish a burst, resume → asserts zero loss/dupes
    (`KafkaPipelineTest`).
21. **What if Kafka is down?** Ingestion degrades to the synchronous path automatically.

## Risk & ML
22. **Risk scoring?** A pipeline of `RiskFactor`s (frequency, geo, admin sensitivity, baseline
    z-score, ML) combined into a band; weights are config/DB-driven.
23. **Behavioral baselines?** Per-entity rolling mean/std via Welford's algorithm (hot in Redis,
    persisted); cold-start entities are skipped, not flagged.
24. **ML model?** IsolationForest + SHAP in a FastAPI service; hybrid = rules + model, with the model
    capped in weight.
25. **ML honesty — how do you avoid overclaiming?** `docs/ml-evaluation.md` reports rules-only vs
    model-only vs hybrid precision/recall on a labeled set; the model is one bounded factor, not a
    black-box verdict; it degrades to rules if the service is down.
26. **What stops data leakage in ML eval?** Deterministic seeds, train/test separation, honest
    metrics; simulated data limitation is stated.

## AI guardrails
27. **What does the AI do?** Investigation summaries + safe NL search — never decisions. The
    ALLOW/STEP-UP/BLOCK decisions are computed deterministically; AI only phrases the explanation.
28. **Prompt-injection defense?** Input sanitizer + data wrapping (untrusted content is delimited and
    labeled), and an **evidence validator** that rejects claims not grounded in provided evidence.
29. **Faithfulness?** Supported-claims / total-claims must exceed `min-faithfulness`, else the
    analysis is marked invalid and the fallback is used.
30. **SSRF via the LLM?** The HTTP client only ever calls the configured host (https or localhost),
    redirects disabled; no user-supplied URLs.
31. **Cost/abuse control?** Token/cost budgets, per-org quota, circuit breaker, timeouts + retries;
    exceeding any → deterministic fallback.

## SOAR (playbooks)
32. **Response model?** AI/analyst proposes → human approves → execute against mock firewall/identity
    adapters; state machine PROPOSED→APPROVED→EXECUTED→ROLLED_BACK (+REJECTED/FAILED/EXPIRED).
33. **Safety rails?** Protected targets (admins, allow-listed IPs, internal net) never acted on;
    destructive actions need an allow-list; two-person rule + admin-risk guard for HIGH/CRITICAL;
    approvals expire; execute is idempotent; every transition audited with before/after + rollback.

## Ops, observability, scale
34. **Observability?** Micrometer → Prometheus (`/actuator/prometheus`); Grafana dashboards as code
    (API rate/errors/p50-95-99, JVM, DB pool, Kafka lag); alert rules; optional OTel tracing linked
    by `traceId`.
35. **SLOs?** `docs/slo.md` — availability 99%, read p95 < 300 ms, error rate < 1%, detection p95 <
    5 s; burn-rate alerts.
36. **Load-test results?** Reads ~1.2k req/s @ p95 94 ms (cached); writes bound by Kafka+DB on an
    8 GB dev box (single ingest ~130 ms) — a dedicated host scales higher. See `docs/performance.md`.
37. **How does it deploy at $0?** Docker Compose + Caddy (auto-HTTPS) + optional Cloudflare Tunnel;
    images on GHCR; GitHub Actions CI/CD. No AWS (ADR-003).
38. **How would you scale it?** Split hot modules into services, move to K8s (manifests stubbed),
    managed MySQL/Redis/Kafka, horizontal API replicas (already stateless); see ADR-003 for the AWS
    path.
39. **Biggest tradeoffs?** Single-node (no HA) and simulated data for honesty vs. production realism;
    modular monolith over microservices for cost/simplicity.
40. **What would you do next?** Real integrations (firewall/IdP), more detection rules, proper OTel
    traces across Kafka, multi-tenant hardening, and a production AWS deployment with managed
    services and autoscaling.
