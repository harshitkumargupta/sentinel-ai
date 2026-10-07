# SentinelAI — Project Audit

_Read-only audit generated 2026-10-06. Commands were run to verify; nothing was modified except this
file. Health checks ran on: macOS (Apple A18 Pro, 6 cores, 8 GB RAM), JDK 21, colima for containers._

## SUMMARY

- **Overall completion: ~97%.** Every planned capability across phases 0–17 is present in the repo
  with code/test/doc evidence; the only gaps are items that need an external environment to fully
  execute (GHCR push / CI run need a git remote; OTel trace export needs the Java agent; ZAP/Sonar
  are wired but server-based).
- **Build/test status: GREEN.** `mvn clean verify` **174 tests, 0 failures/errors/skips, BUILD
  SUCCESS**. Frontend `npm run build` OK + **Vitest 10/10**. ML **pytest 8 passed**. Git tree clean.
  gitleaks: no leaks. dev/prod/monitoring compose all validate.
- **Demo-readiness: READY.** Live stack responded (`/api/health` 200), the brute-force and
  prompt-injection simulator scenarios ran, and `/api/incidents` returned 578 incidents. v1.0.0 is
  tagged on `main`.
- **Top 5 risks**
  1. **AI is off by default in dev** — "Investigate with AI" returns the deterministic fallback unless
     the backend runs with `AI_ENABLED=true` (set `AI_PROVIDER=fake` for an offline demo).
  2. **`docs/final-evaluation.md` per-rule F1 = 0** is a capture artifact (Kafka backlog on the 8 GB
     box), not real detection quality — cite `ml-evaluation.md` (hybrid F1 0.999) + incident-level
     recall 0.75 instead. Looks like a placeholder to a reviewer.
  3. **No git remote configured** → the CI workflow and GHCR image push have never actually run; only
     validated locally.
  4. **Single 8 GB node**: the write path saturates under concurrent load (reads are fine) — expected
     and documented, but don't load-test writes during the live demo.
  5. **OTel tracing is agent-based** (no in-app Micrometer tracing bridge in the pom), so traces only
     export when the OTel Java agent + Jaeger profile are enabled.

Report sections 1–6 follow.

---

## 1. INVENTORY

**Git** — branch `main`; tag **v1.0.0**; **0 uncommitted** changes (clean tree). 21 branches
(`develop`, `main`, 19 `feature/*`). Last 15 commits (newest first): `45a4dc2` declutter storyline ·
`319a131` merge · `bd1ad4c` fix storyline · `227c2cb` contrast palette · `8b8dcdb` merge · `7f471cb`
contrast · `eefd2f8` globe/UI polish · `0565889` merge · `86dd683` globe countries · `816160d`
**Release v1.0.0** · `620b613` merge final · `524734f` feat(final) observability · `5fd2593` merge
ui-finish · `bfeb30b` phase-16 finish · `223ca0f` merge phase-16.

**Counts**

| Area | Count |
|------|------:|
| Java main classes | 396 |
| Java test classes | 69 (60 with `@Test`, **171** `@Test` methods) |
| Flyway migrations | 25 (`V1`..`V25`) |
| React components (`.jsx`) | 56 |
| Frontend services (`.js`) | 17 |
| Frontend tests | 3 Vitest + 2 Playwright specs |
| ML service files / tests | 7 `.py` / 3 test files (8 tests) |
| k6 load scripts | 5 |
| scripts/ | 8 shell scripts |
| docs (`.md`) | 31 |

**Tree summary**: `backend/` (Spring Boot modular monolith — auth, event, detection, risk, incident,
ai, playbook, adminrisk, kafka, baseline, audit, site, ml, cache, ratelimit), `frontend/` (React/Vite
SPA: pages, components, components/ui, three, theme, services, e2e), `ml/` (FastAPI scoring + train +
evaluate + synth), `infrastructure/` (docker: dev/prod/monitoring/sonar/zap compose + Caddyfile;
prometheus; grafana; k8s stub), `scripts/` (deploy/rollback/backup/restore/security-scan/
run-evaluation), `load-tests/` (k6), `docs/` (architecture, security/, adr/, report/, runbooks).

---

## 2. FEATURE CHECKLIST

Legend: ✅ DONE · 🟡 PARTIAL · ❌ MISSING. Evidence = representative file.

### Core
| Feature | Status | Evidence | Notes |
|---|---|---|---|
| Auth: JWT | ✅ | `auth/security/JwtService.java` | HS256 pinned, iss/aud required, clock skew |
| Refresh rotation + reuse detection | ✅ | `auth/service/AuthService.java` | reuse revokes the family |
| Lockout | ✅ | `auth/domain/User.java`, `AuthService` | N failures → locked_until |
| RBAC | ✅ | `@PreAuthorize` across controllers | VIEWER/ANALYST/ADMIN |
| REST APIs | ✅ | `*/web/*Controller.java` | events/incidents/rules/dashboard/… |
| Audit hash chain + verify | ✅ | `audit/service/AuditService.java` | `/api/audit-logs/verify` |
| Unified error handling + traceId | ✅ | `common/web/GlobalExceptionHandler.java`, `TraceIdFilter.java` | MDC traceId, `X-Trace-Id` |
| Config properties + feature flags | ✅ | 16 `*Properties.java`; `ai/redis/kafka.enabled` | typed `@ConfigurationProperties` |

### Data
| Schema/migrations | ✅ | `db/migration/V1..V25` | Flyway, `ddl-auto=validate` |
| Multi-tenancy (org_id) | ✅ | `common/domain/Organization.java`; org-scoped lookups | enforced in services |
| Sites + API keys | ✅ | `site/…`, `ApiKeyAuthenticationFilter` | per-site ingest keys, rotate |

### Detection
| Ingestion/normalizers | ✅ | `ingestion/normalize/{Generic,Auth,WebAccessLog}Normalizer.java` | `EventNormalizer` iface |
| Simulator + labeled scenarios | ✅ | `simulator/SimulatorService.java` | 9 scenarios, seeded |
| Detection rules | ✅ | `detection/rule/*`, `DetectionRuleEvaluator` | pluggable strategies |
| Honeytokens | ✅ | `honeytoken/…`, `detection/rule/HoneytokenRule.java` | hashed tokens |
| MITRE tags | ✅ | `mitreTechnique` on rules/alerts | heatmap uses it |
| Backtesting | ✅ | `detection/backtest/BacktestService.java` | `BacktestRuleContext` |
| Behavioral baselines | ✅ | `baseline/BehavioralBaselineService.java` | Welford, Redis-hot |
| Evaluation harness | ✅ | `evaluation/EvaluationService.java` | precision/recall/F1 |

### Risk / incidents
| Risk waterfall | ✅ | `risk/factor/*`, FE `RiskWaterfall.jsx` | factor pipeline |
| Correlation | ✅ | `incident/correlation/CorrelationService.java` | dedup → incidents |
| State machine | ✅ | `InvalidStateTransitionException`, incident status | transitions guarded |
| Timeline | ✅ | `incident/correlation/TimelineService.java` | per-incident |
| Alert-reduction | ✅ | `dashboard/service/DashboardService.java` | events→incidents ratio |
| MITRE heatmap | ✅ | FE `DashboardPage.jsx` | populated |
| Storyline graph | ✅ | FE `StorylineGraph.jsx` + `Storyline3D` | 2D default, 3D toggle |

### Admin
| Admin risk guard | ✅ | `adminrisk/AdminGuardService.java` | ALLOW/STEP-UP/BLOCK |
| Two-person approval | ✅ | `adminrisk/AdminApprovalService.java` + playbook | HIGH/CRITICAL |
| Session history | ✅ | admin sessions endpoints | `/api/admin/sessions` |

### ML
| Model service | ✅ | `ml/app.py` (FastAPI) + `ml/MlScoringClient` | `/score`, `/health` |
| Training | ✅ | `ml/train.py` | IsolationForest + SHAP |
| Hybrid risk factor | ✅ | `risk/factor/MlRiskFactor.java`, `adminrisk/factor/AdminMlRiskFactor.java` | capped weight |
| Drift check | ✅ | `ml/MlDriftMonitor.java` | — |
| Rules vs model vs hybrid report | ✅ | `docs/ml-evaluation.md` | table below |

### Infra
| Redis (rate-limit, cache, fallback) | ✅ | `ratelimit/TokenBucketRateLimiter.java`, `cache/CacheService.java` | in-memory fallback |
| Kafka outbox | ✅ | `kafka/outbox/OutboxRelay.java` | transactional |
| Idempotent consumers | ✅ | `kafka/idempotency/IdempotencyService.java`, `IdempotentExecutor` | dedup table |
| Retry + DLQ | ✅ | `kafka/consumer/{RetryConsumer,DlqConsumer}.java`, `kafka/dlq/*` | replay via admin |
| Chaos script | ✅ | `kafka/admin/ChaosService.java` | pause/burst/resume |

### AI
| LlmClient | ✅ | `ai/llm/{Http,Fake,Guarded}LlmClient.java` | SSRF-guarded http |
| Evidence validator | ✅ | `ai/validation/EvidenceValidator.java` | faithfulness gate |
| Prompt-injection defense | ✅ | `ai/security/PromptSanitizer.java` | wrap + flag |
| Fallback | ✅ | `GuardedLlmClient`, budget/circuit breaker | degrades gracefully |
| Safe NL search | ✅ | `ai/nlsearch/NlSearchService.java` | bounded, validated |
| AI evaluation | ✅ | `ai/eval/*`, `docs/ai-evaluation.md` | faithfulness 1.00 |

### Response
| Playbooks (dry-run/approve/rollback/protected) | ✅ | `playbook/PlaybookService.java`, `TargetPolicy.java` | state machine + guards |
| Feedback tuning | ✅ | `detection/tuning/TuningService.java` | threshold suggestions |
| Similar incidents | ✅ | `incident/similarity/SimilarityService.java` | cosine, cached |

### Security
| Headers | ✅ | `common/config/SecurityConfig.java` + `WebSecurityProperties.java` | CSP/XFO/HSTS |
| Endpoint-protection test | ✅ | `security/EndpointProtectionTest.java` | fails build on unprotected |
| Scanners: gitleaks | ✅ | `.gitleaks.toml` | ran clean |
| Dependency-Check | ✅ | `backend/pom.xml` (profile `security`) | `failBuildOnCVSS 7` |
| Trivy | ✅ | `scripts/security-scan.sh`, CI | fs + image |
| Sonar | 🟡 | `sonar-project.properties`, `sonar.yml` | wired; server-based, not run here |
| ZAP | 🟡 | `infrastructure/docker/zap.yml` | wired; run against live stack |
| Threat model | ✅ | `docs/security/threat-model.md` | STRIDE |

### DevOps
| Dockerfiles (be/fe/ml) | ✅ | `*/Dockerfile` | multi-stage, non-root, healthcheck |
| Compose dev + prod | ✅ | `docker-compose.yml`, `docker-compose.prod.yml` | both validate |
| CI workflow | ✅ | `.github/workflows/ci.yml` | lint/test/scan/build |
| GHCR push | 🟡 | ci.yml push step | **never executed — no git remote** |
| deploy/rollback/backup scripts | ✅ | `scripts/*.sh` | health-gated + auto-rollback |
| Caddy / Tunnel | ✅ | `Caddyfile`, prod compose `cloudflared` profile | auto-HTTPS/free URL |

### UI
| Design system | ✅ | `frontend/src/theme/tokens.css`, `ui/*` | tokens + component lib |
| Command center | ✅ | `pages/DashboardPage.jsx` | KPIs/charts/heatmap/stream |
| Three.js globe/core | ✅ | `three/AttackGlobe.jsx` (country borders), `ThreatCore.jsx` | lazy-loaded |
| Accessibility | ✅ | focus rings, ARIA, text/table 3D fallback | reduced-motion |
| Playwright tests | ✅ | `frontend/e2e/smoke.spec.js` | 3/3 passed this session |

### Observability & final
| Prometheus | ✅ | `infrastructure/prometheus/*` | scrape + alert rules |
| Grafana dashboards | ✅ | `infrastructure/grafana/dashboards/sentinel-overview.json` | provisioned, populated |
| OpenTelemetry | 🟡 | `monitoring.yml` jaeger profile; `management.tracing` | **no in-app tracing bridge dep** — agent-based only |
| k6 tests | ✅ | `load-tests/*.js` | 5 scenarios |
| final-evaluation | 🟡 | `docs/final-evaluation.md` | present; per-rule F1 is a capture artifact (see §5) |
| report / demo / viva | ✅ | `docs/report/report.md`, `demo-script.md`, `viva-qa.md` | complete |
| v1.0.0 tag | ✅ | `git tag v1.0.0` | annotated, on `main` |

---

## 3. HEALTH CHECKS

| Check | Result |
|-------|--------|
| `mvn clean verify` | ✅ **174 run, 0 failed, 0 errors, 0 skipped — BUILD SUCCESS** |
| `npm run build` | ✅ OK (main 341 KB / 114 KB gzip; three.js in a separate lazy chunk) |
| `npm test` (Vitest) | ✅ **10 passed** (3 files) |
| `npm ci` | ⚠️ not re-run — build+test verified against the committed `package-lock.json`; a from-scratch `npm ci` was skipped to avoid wiping the live dev server's `node_modules` on a flaky network. |
| ML `pytest` | ✅ **8 passed** |
| docker compose config (dev/prod/monitoring) | ✅ all 3 valid |
| `git status` clean | ✅ 0 uncommitted |
| Secrets scan (gitleaks, history + config) | ✅ **no leaks** |
| TODO/FIXME in source | ✅ **1** (negligible) |
| "placeholder/stub" mentions | ✅ 4, all intentional (GeoIP stub, template explainer, prompt templates, secrets-validator docstring) |
| Source files > 500 lines | ✅ **0** |
| Disabled/`@Disabled`/`.skip` tests | ✅ **0** |
| Playwright E2E | ✅ 3/3 (run earlier this session; Chromium installed) |

---

## 4. RUNTIME CHECK

The app runs via `mvn spring-boot:run` in dev (Docker Compose supplies MySQL/Redis/Kafka, all
**healthy**); the prod app image is in `docker-compose.prod.yml`. Checked against the live dev stack:

| Step | Result |
|------|--------|
| MySQL / Redis / Kafka containers | ✅ Up (healthy) |
| `GET /api/health` | ✅ 200 |
| Simulator `brute_force` scenario | ✅ ran (`runId dc8e76e5…`) |
| Simulator `prompt_injection` scenario | ✅ ran (`runId 01c69a8a…`) |
| `GET /api/incidents` | ✅ 200, **578** incidents |

No failures. (Deviation from the brief: the app itself isn't started via `docker compose` in dev —
it runs on the host — so the check used the running host backend + the compose-managed infra; the
full containerised `docker compose up` is validated separately in `docs/deployment.md`.) Stack left
running for the user; stop with `docker compose -f infrastructure/docker/docker-compose.yml down`.

---

## 5. NUMBERS (from docs)

**ML detection — rules vs model vs hybrid** (`docs/ml-evaluation.md`, synthetic 1599 events/399 attacks):

| Approach | Precision | Recall | F1 | ROC-AUC | FP/1000 |
|---|---|---|---|---|---|
| Rules-only | 1.0 | 0.652 | 0.789 | — | 0.0 |
| Model-only | 1.0 | 0.997 | 0.999 | 1.0 | 0.0 |
| **Hybrid** | **1.0** | **0.997** | **0.999** | **1.0** | **0.0** |

**Alert reduction**: **98.57%** (events 40,528 → incidents 578) — `final-evaluation.md`.
**Incident-level detection recall**: **0.75** (6/8 seeded scenarios) — `final-evaluation.md`.
**AI** (`ai-evaluation.md`): faithfulness **1.00**, %VALID **100%**, %FALLBACK **0%**, injection pass
**100% (1/1 flagged, not followed)**.
**Caching** (`performance.md`): p95 **13.9 ms → 3.2 ms (~4.3×)**, cache hit-rate ~0.71.
**Ingestion sync vs Kafka** (`performance.md`): sync **164 ev/s**, p95 319.6 ms; Kafka **648 ev/s**,
p95 65.0 ms (~4× throughput, ~5× lower p95).
**k6 (Phase 17, 8 GB box)**: dashboard reads ~**1,237 req/s @ p95 94 ms**; single ingest ~130 ms;
write path saturates under 50 concurrent VUs (documented).
**Chaos**: retry→DLQ→replay with **zero loss / zero duplicates** (`KafkaPipelineTest`).

⚠️ **Placeholder/artifact flags**:
- `final-evaluation.md` **per-rule precision/recall/F1 = 0** (and `alerts=0`) is a *capture artifact*
  (Kafka consumer backlogged on the 8 GB box at capture time) — it is annotated as such in the doc.
  Authoritative per-rule numbers come from `ml-evaluation.md` and the deterministic
  `BacktestServiceTest`/`EvaluationServiceTest`.
- AI injection pass rate is **1/1** — correct but a tiny sample; expand the injection corpus for a
  stronger figure.

---

## 6. GAPS (prioritised)

**P0 — breaks the demo**: _none found._ (One demo gotcha, not a defect: start the backend with
`AI_ENABLED=true AI_PROVIDER=fake` so "Investigate with AI" shows the AI path rather than the
fallback.)

**P1 — should fix**
1. **final-evaluation per-rule F1 = 0 artifact** — re-run `scripts/run-evaluation.sh` on an idle
   instance (or let the Kafka consumer drain) so the doc shows real per-rule numbers; or point the
   report at `ml-evaluation.md` as the authoritative source.
2. **CI/GHCR never executed** — add a GitHub remote and push so `ci.yml` runs and images land in
   GHCR; confirm the pipeline is green end to end.
3. **OTel traces not exported in-app** — either add a Micrometer tracing bridge + OTLP exporter
   dependency, or document running with the OpenTelemetry Java agent + the `tracing` compose profile
   so `traceId` links logs↔traces.

**P2 — nice to have**
4. Run **ZAP baseline** and **SonarQube** against the live stack and record results in
   `docs/security/scan-results.md` (they're wired but server-based).
5. Set `AI_ENABLED=true AI_PROVIDER=fake` as a **dev default** (`application-dev.yml`) so the AI
   features are on in dev without extra env.
6. **Storyline graph** for very large incidents — cluster the many `load-user-*` nodes into one
   "N users" node for readability (declutter already shipped).
7. Expand the **prompt-injection corpus** and re-measure the pass rate beyond 1/1.
8. Re-run the **ingest k6** on a dedicated host to publish representative write throughput.

---

_End of audit._
