# Scan Results — Phase 14

Before/after for the hardening pass. "Before" = `develop` prior to this branch; "after" = this
branch. Reports are written to `reports/` (gitignored) by `scripts/security-scan.sh`.

Environment: macOS, JDK 21, colima for containers. Dates: 2026-10-06.

## Summary (before → after)

| Tool / check | Scope | Before | After | Notes |
|--------------|-------|--------|-------|-------|
| gitleaks | git history + working tree | — | **0 leaks** | Dev/test secrets allow-listed in `.gitleaks.toml`; 14 commits + 8.6 MB tree scanned |
| Security regression tests | backend (JUnit) | 0 tests | **24 passing** | `com.sentinelai.security.*` + `JwtSecurityTest` |
| Endpoint access-control guard | all MVC endpoints | none | **enforced** | `EndpointProtectionTest` fails the build on a new unprotected endpoint |
| npm audit (high/critical) | frontend | 1 high, 3 moderate | 1 high, 3 moderate | All in `vite` (devDependency); production bundle unaffected — accepted **AR-04** |
| Security headers | API responses | missing (XFO default only) | **CSP, XFO, nosniff, Referrer/Permissions-Policy, HSTS(prod)** | `SecurityHeadersTest` |
| Trivy filesystem/IaC (HIGH,CRITICAL) | repo + Dockerfiles | — | see `reports/trivy-fs.json` | Run via `scripts/security-scan.sh` |
| OWASP Dependency-Check (CVSS≥7) | backend deps | — | wired (`mvn -Psecurity verify`) | Local NVD sync is slow without `NVD_API_KEY` — see **AR-01** |
| SonarQube quality gate | full repo | — | wired (`infrastructure/docker/sonar.yml`) | Server-based; gate: 0 new blocker/critical, ≥70% new-code coverage |
| OWASP ZAP baseline | running API | — | wired (`infrastructure/docker/zap.yml`) | DAST; run against a live stack, triage here |

## What was fixed in this pass

- **A05/Misconfig**: added CSP / `X-Frame-Options` / `nosniff` / `Referrer-Policy` /
  `Permissions-Policy` + prod HSTS; moved CORS to a config allow-list that rejects `*`+credentials;
  locked actuator to health/info/prometheus (rest ADMIN).
- **A07/Auth**: JWT issuer+audience now required, `alg` pinned to HS256, clock skew bounded;
  refresh-token **reuse detection** (family revoke); config password policy.
- **A01/Access control**: org-scoped `UserService.list/update/disable` (was cross-tenant); added the
  endpoint-protection enumeration test.
- **Error handling**: malformed/oversized JSON now returns **400** (was **500** — the
  `HttpMessageNotReadableException` fell through to the catch-all); added handlers for missing
  params, type mismatch, and method-not-allowed; the catch-all now logs server-side.
- **DoS**: Jackson depth/size limits + request-size caps.
- **A10/SSRF**: LLM client restricted to the configured host, https-or-localhost, redirects off.
- **Robustness**: fuzzing the ingest normalizers (3,000 malformed inputs) — none throw.

## Accepted / deferred

See [`accepted-risks.md`](accepted-risks.md): AR-01 (Dependency-Check in a profile), AR-02 (CSP
`style-src 'unsafe-inline'` for Vite), AR-03 (CSRF n/a for stateless bearer), AR-04 (vite dev-only
advisories).
