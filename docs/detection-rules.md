# SentinelAI — Detection Rules

The engine evaluates every ingested event against the enabled rules for its org (see
[detection.md](detection.md) for the pipeline). Rules are **pluggable** (`DetectionRuleEvaluator`
beans, matched by `rule_type`) and **data-driven**: all thresholds/windows live in each rule's
`config` JSON, editable by an admin via `PUT /api/rules/{id}` or the Admin UI — no redeploy. A
firing produces an **alert** (not an incident; incidents come later).

## Rule catalog

| `rule_type` | Fires when | `config` keys (defaults) | Default severity | MITRE |
|---|---|---|---|---|
| `BRUTE_FORCE` | ≥ `threshold` FAILED_LOGIN for one account/IP in the window | `threshold` (10), `windowSeconds` (300), `groupBy` (`username`\|`sourceIp`) | HIGH | T1110 |
| `CREDENTIAL_STUFFING` | ≥ `distinctUsers` distinct accounts fail from one IP in the window | `distinctUsers` (5), `windowSeconds` (300) | HIGH | T1110.004 |
| `HIGH_FREQUENCY_API` | ≥ `threshold` API_ABUSE events from one IP in the window | `threshold` (100), `windowSeconds` (60), `groupBy` (`sourceIp`) | MEDIUM | T1499 |
| `SUSPICIOUS_LOGIN` | SUSPICIOUS_LOGIN from a new country, or at an odd hour (UTC) | `oddHourStart` (0), `oddHourEnd` (5) | MEDIUM | T1078 |
| `IMPOSSIBLE_TRAVEL` | login from a different country than the previous one, sooner than `minSecondsBetweenCountries` | `minSecondsBetweenCountries` (3600) | HIGH | T1078 |
| `ABNORMAL_ACCESS` | an ABNORMAL_ACCESS event (optionally under `resourcePrefix`) | `resourcePrefix` (none) | HIGH | T1548 |
| `HONEYTOKEN` | an incoming value hashes to a row in `honeytokens` (always CRITICAL, bumps `triggered_count`) | — | CRITICAL | T1078.001 |

`groupBy` ∈ `username` \| `sourceIp` \| `entityKey`. Example config:

```json
{ "threshold": 10, "windowSeconds": 300, "groupBy": "username" }
```

## How rules count

Windows are evaluated relative to the **event's own timestamp** (not wall-clock), so live detection
and backtests agree and historical/simulated events are handled correctly. Live frequency counts
come from the `WindowStore` (in-memory now, Redis later); distinct-user, recent-id and
previous-login lookups come from the event store. One failing rule is caught, logged with the
request trace id, and counted as a metric — it never blocks the other rules.

## Backtesting

`POST /api/rules/{id}/backtest` with `{ "configOverride": {...}, "from": "...", "to": "..." }`
replays stored events in that window through the rule (dry run — **no alerts saved**) and returns
`eventsScanned`, `alertsFired`, and sample alert messages. Each run is recorded in `backtest_runs`.

## Simulator & evaluation

`POST /api/simulator/run` (ADMIN, requires `sentinel.simulator.enabled=true`) generates
deterministic, labeled events for these scenarios: `normal`, `brute_force`, `credential_stuffing`,
`suspicious_login`, `impossible_travel`, `api_abuse`, `abnormal_access`, `honeytoken`. Labels are
stored in `sim_labels`. `GET /api/evaluation/detection?runId=…` then scores detection against the
labels — overall and per-rule precision/recall/F1 plus mean detection latency.

Reference run (seed 42, intensity 5, all scenarios): **overall precision ≈ 0.99, recall 1.00,
F1 ≈ 1.00** across 174 labeled events.
