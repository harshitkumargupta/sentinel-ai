# SentinelAI — Detection Engine

The detection engine turns raw `security_events` into `incidents` by evaluating **data-driven,
admin-editable rules**. It is pluggable, decoupled, and gated by a feature flag.

## Flow

```
EventService.create / SecurityEventRecorder.record
        │  saves SecurityEvent
        ▼
  publishes SecurityEventCreatedEvent   (Spring ApplicationEvent)
        ▼
  DetectionEngine.onSecurityEvent   @EventListener @Transactional
        │  for each enabled rule in the event's org:
        │     strategy = registry[rule.ruleType]
        │     outcome  = strategy.evaluate(event, rule, context)
        ▼
  applyOutcome → create OR correlate an Incident, link events, write audit
```

The event module never references detection — it only publishes an event — so detection can be
added, removed, or disabled without touching event ingestion.

## Strategies (pluggable)

A detection type is a Spring bean implementing `DetectionStrategy` (`type()` + `evaluate(...)`).
The engine discovers strategies by `type()`; **adding a new rule type is a new bean + config, with
no engine edits**. Rule parameters come from the rule's `config` JSON (editable by an ADMIN via the
rules API — no redeploy).

| `rule_type` | Strategy | config | Fires when |
|---|---|---|---|
| `THRESHOLD` | `ThresholdRuleEvaluator` | `{eventType, threshold, windowSeconds, groupBy}` | ≥ threshold events of a type for the same group value within the window |
| `RATE_LIMIT` | `RateLimitRuleEvaluator` | `{eventType, requestsPerMinute, groupBy}` | ≥ requestsPerMinute events in a fixed 60s window (default group: source IP) |
| `GEO_VELOCITY` | `GeoVelocityRuleEvaluator` | `{eventType}` | a user logs in from a different country than their previous login |

`groupBy` ∈ `username` \| `sourceIp` \| `entityKey`.

## Correlation

Each outcome carries a stable `correlationKey` (`rule:<id>:<groupBy>:<value>`). If an **active**
incident (OPEN / INVESTIGATING / CONTAINED) with that key exists, the new events are linked to it;
otherwise a new incident is created. This prevents a flood of duplicate incidents while an attack
is ongoing.

## Configuration (`sentinel.detection.*`, typed `@ConfigurationProperties`)

| Property | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master feature flag; when false the engine evaluates nothing |
| `max-linked-events` | `100` | Cap on events linked to an incident per evaluation |
| `severity-scores` | LOW 25 / MED 50 / HIGH 75 / CRIT 100 | Base `risk_score` for a new incident by the firing rule's severity |

## Resilience

- One failing rule is caught and logged (with the rule id); it never blocks the other rules.
- Time comes from an injected `Clock`, so windows are deterministic and testable.
- Full risk scoring (weighted `RiskFactor`s) arrives in a later phase; for now `risk_score` is the
  severity-based base above.
