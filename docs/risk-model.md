# SentinelAI — Risk Model

Each incident gets a deterministic risk score in **0–100** and a severity, computed by composing
pluggable **risk factors**. Scores are recomputed on every alert that joins the incident.

## Formula

```
score   = min( cap , Σ factor.points )           // cap default 100
severity = CRITICAL if score ≥ criticalAt (80)
           HIGH     if score ≥ highAt     (50)
           MEDIUM   if score ≥ mediumAt   (25)
           LOW      otherwise
```

Factors are evaluated and ordered by name, so identical input always yields the identical score and
breakdown (deterministic). Each factor returns `{name, points, reason}`; the reasons form the
human-readable risk waterfall shown in the UI.

## Factors & weights (defaults — all in `sentinel.risk.*`, overridable per profile/env)

| Factor | What it measures | Points (default) |
|---|---|---|
| `severity` | Highest alert severity in the incident | LOW 10 / MED 25 / HIGH 40 / CRIT 60 |
| `frequency` | Number of contributing events | 2 each, cap 20 |
| `repetition` | Repeated rule firings (extra alerts) | 5 per extra alert, cap 20 |
| `asset_criticality` | Max asset criticality of touched assets | 8 per level, cap 32 |
| `honeytoken` | Any honeytoken hit | 60 (flat) |
| `user_behavior` | New country / impossible travel, odd hour, unusual resource | 15 + 10 + 10, cap 30 |
| `mitre_stage` | Later kill-chain stage weighs more (by MITRE id) | per `mitreStageWeights` (e.g. T1110 5 … T1548 20) |

`cap`, `mediumAt`, `highAt`, `criticalAt`, every weight and the MITRE-stage map are
`@ConfigurationProperties` (the editable risk configuration) — change them without code.

Add a new factor by adding a `RiskFactor` bean (`FactorResult score(RiskContext)`); `RiskService`
composes all of them automatically.

## Example

Credential-stuffing incident (8 failed logins, one HIGH alert, MITRE T1110.004):

```
severity          +40   Highest alert severity is HIGH
frequency         +16   8 contributing events
mitre_stage       +5    Kill-chain weight for T1110.004
──────────────────────
score 61 → HIGH
```

## Correlation & incidents

Alerts are grouped into incidents by entity (user/IP) within `sentinel.correlation.windowSeconds`
(default 3600, measured in **event time**). A new alert joins the most recent open incident for the
same entity, or starts a new one past the window (distinct episode). Joining is idempotent (unique
`(incident_id, alert_id)` / `(incident_id, event_id)`), rescored each time, and auto-escalated —
crossing into HIGH/CRITICAL writes a timeline entry and notifies admins. State machine:
`OPEN → INVESTIGATING → CONTAINED → RESOLVED`, with `FALSE_POSITIVE` from any open state; illegal
transitions raise `InvalidStateTransitionException` (HTTP 409). Every change is recorded in
`incident_timeline`.

## Reference run (seed 42, intensity 5)

174 events → 33 alerts → **10 incidents (94% alert reduction)**; incident-level precision/recall
**1.0** (7/7 attack scenarios detected).
