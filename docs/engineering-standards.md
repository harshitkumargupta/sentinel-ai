# SentinelAI — Engineering Standards

These are the engineering standards for SentinelAI. **Follow them in all work.** When touching
existing code that predates a standard, bring it into line opportunistically (don't rewrite the
world in one PR, but don't add new code that violates these rules).

> Status note: the project is mid-build. Some items below are already in place (e.g. a single
> `@RestControllerAdvice`, JSON 401/403 handlers, DTOs at the boundary, lockout/token config);
> others are the target to converge on (full `SentinelException` hierarchy with error-code enums,
> MDC `traceId`, typed `@ConfigurationProperties`, structured JSON logging). New work meets the
> full standard; legacy code is migrated as it's touched.

---

## Error handling

- **One exception hierarchy** in `common.exception`: `SentinelException` (base) →
  `NotFoundException`, `ValidationException`, `ConflictException`, `UnauthorizedException`,
  `ForbiddenException`, `RateLimitException`, `ExternalServiceException`,
  `InvalidStateTransitionException`. Each carries:
  - an **error code enum** (e.g. `AUTH_001`, `EVENT_404`),
  - the **HTTP status**, and
  - a **safe, client-facing message** (no internals).
- **One `@RestControllerAdvice`** maps every exception — including Spring's
  (`MethodArgumentNotValidException`, malformed JSON / `HttpMessageNotReadableException`,
  missing params, type mismatch, `HttpRequestMethodNotSupportedException`, `AccessDeniedException`,
  authentication failures, `DataIntegrityViolationException` / constraint violations) — to **one
  error body**:
  ```json
  {
    "timestamp": "2026-10-03T16:00:00Z",
    "status": 400,
    "code": "EVENT_422",
    "message": "Human-readable, safe message",
    "path": "/api/v1/events",
    "traceId": "9f1c…",
    "fieldErrors": [{ "field": "severity", "message": "must not be null" }]
  }
  ```
- **Never leak** stack traces, SQL, or internal details to the client. Log them **server-side**
  with the `traceId`.
- **`traceId` via MDC**: generate one per request, return it in the `X-Trace-Id` response header,
  and include it in every log line.
- **Never swallow exceptions**: handle meaningfully or rethrow with context. **No empty catch
  blocks.**
- **External calls** (LLM, GeoIP, Kafka, Redis) use **timeouts, retries with backoff, and a
  fallback**. Failure in an **optional** dependency (Redis, AI) must **degrade gracefully** and
  never take down the core API.
- Spring Security **entry point (401)** and **access-denied handler (403)** return the **same JSON
  error format**.

## Flexibility / configuration

- **No magic numbers.** All thresholds, limits, TTLs, token lifetimes, lockout rules, page-size
  caps, and feature flags live in typed **`@ConfigurationProperties`** classes, **`@Validated`**,
  overridable **per profile** and via **env var**.
- **Feature flags** for optional modules so the app runs with any subset enabled:
  `ai.enabled`, `redis.enabled`, `kafka.enabled`, `honeytokens.enabled`.
- **Program to interfaces**: `DetectionRule`, `RiskFactor`, `EventNormalizer`, `LlmClient`,
  `NotificationChannel`, … New rules / risk factors / integrations are added by **implementing an
  interface plus config — no edits to the engine.**
- **Data-driven**: detection rule parameters and risk weights live in DB/JSON config, editable by
  an admin **without redeploying**.
- **API**: consistent pagination / sorting / filter params; **versioning-ready** (`/api/v1` alias);
  **ISO-8601 UTC** timestamps everywhere.

## Quality

- **Validate at the boundary** (Bean Validation on DTOs) **and** in the domain (service-level
  rules). **Sanitize and size-limit** free-text and JSON payloads.
- **DTOs only at the API boundary — never expose entities.** Services are **transactional with
  explicit boundaries**. Make operations **idempotent** where retries are possible.
- **Structured logging** (JSON in the `prod` profile). **No secrets or PII in logs.** Use log
  levels sensibly (`ERROR` actionable, `WARN` recoverable, `INFO` lifecycle, `DEBUG` detail).
- **Tests for every new class of behavior, including failure paths**: bad input, missing resource,
  dependency down, forbidden role. Prefer **deterministic** tests — inject `Clock`, seed randomness.
- **Small, single-purpose classes; no god services.** Javadoc only where intent isn't obvious.
- **Frontend**: a **central Axios error interceptor**, a **global error boundary**, explicit
  **loading / empty / error states on every data view**, and **user-friendly messages derived from
  the API error `code`**.

---

## Conventions (project)

- **Branching**: `feature/*` from `develop`; merge back via PR; `main` is releasable. Conventional
  Commits (`feat:`, `fix:`, `docs:`, `chore:`…).
- **DB**: schema changes only via **Flyway** migrations; `ddl-auto=validate`. Enums as native
  MySQL `ENUM` (uppercase); JSON columns via `@JdbcTypeCode(SqlTypes.JSON)`; hashes `CHAR(64)`.
- **Secrets** come from env vars; never commit them (dev-only defaults may live in
  `application-dev.yml`). `JWT_SECRET` is required outside dev.
- **Verify before done**: `mvn clean verify` and `npm run build` must pass; report failures
  honestly.
