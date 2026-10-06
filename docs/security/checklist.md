# OWASP Top 10 (2021) — Coverage Checklist

Where each category is mitigated in SentinelAI and the test that proves it.

| # | Category | Mitigation in code | Proof |
|---|----------|--------------------|-------|
| A01 | Broken Access Control | Every by-id lookup filters on `org` (incidents, events, analyses, playbook actions, users); method `@PreAuthorize` for role gates; stateless bearer (no CSRF); endpoint enumeration guard fails the build on an unprotected endpoint | `EndpointProtectionTest`, `CrossTenantAccessTest`, `PrivilegeEscalationTest` |
| A02 | Cryptographic Failures | bcrypt password hashes; refresh tokens stored as SHA-256; JWT HS256 with env secret (≥32 chars); HSTS in prod; TLS at the edge | `JwtSecurityTest`, `application-prod.yml` |
| A03 | Injection | Parameterized JPA/Criteria queries; Bean Validation at the boundary; output via JSON encoding; log-injection sanitizer; prompt-injection defense for the LLM | `InjectionPayloadTest`, prompt-injection suite |
| A04 | Insecure Design | Two-person playbook approval, admin-risk guard, feature flags for optional deps, deterministic AI decisioning | `PlaybookFlowTest`, `AdminGuardServiceTest` |
| A05 | Security Misconfiguration | Security headers (CSP, XFO, nosniff, Referrer/Permissions-Policy); actuator limited to health/info/prometheus, rest ADMIN; CORS allow-list (no `*`+credentials); non-root hardened containers | `SecurityHeadersTest`, `EndpointProtectionTest` |
| A06 | Vulnerable & Outdated Components | OWASP Dependency-Check (`failBuildOnCVSS 7`), npm audit, Trivy image/FS scans | `scripts/security-scan.sh`, CI |
| A07 | Identification & Auth Failures | Lockout, generic login errors (no enumeration), JWT validation, refresh rotation + reuse detection, config password policy | `AuthApiTest`, `RefreshTokenReuseTest`, `JwtSecurityTest` |
| A08 | Software & Data Integrity | Hash-chained audit log; Kafka idempotency + DLQ; evidence-validated AI | `AuditChainTest`, `KafkaPipelineTest` |
| A09 | Logging & Monitoring Failures | Structured logging with MDC `traceId`, server-side error logging, self-monitoring security events, audit chain; no secrets/PII in logs | `GlobalExceptionHandler`, `AuditChainTest` |
| A10 | SSRF | LLM client only ever calls the configured host; https-or-localhost; redirects disabled; no user-supplied outbound URLs | `HttpLlmClient` SSRF guard |

CSRF note: the API is stateless and bearer-authenticated (no cookies/session), so CSRF tokens are
not applicable — see `accepted-risks.md` AR-03.
