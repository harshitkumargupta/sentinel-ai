# SentinelAI — Detection Pipeline

How a raw event becomes an **alert**. (Rule catalog and config keys:
[detection-rules.md](detection-rules.md).)

## Flow

```
IngestionService.ingest  (API /api/events/ingest, or the simulator)
        │  normalize (EventNormalizer) → validate/cap → GeoIP enrich → derive entity_key
        │  → dedupe (optional clientEventId) → save SecurityEvent
        ▼
  publishes SecurityEventCreatedEvent        (Spring ApplicationEvent)
        ▼
  SynchronousEventProcessor (EventProcessor)  @EventListener @Transactional
        │  records the event into the WindowStore
        │  for each enabled rule in the event's org:
        │     evaluator = registry[rule.ruleType]           (DetectionRuleEvaluator)
        │     draft     = evaluator.evaluate(event, rule, ctx) → Optional<AlertDraft>
        ▼
  persist Alert (rule snapshot + matched event ids + MITRE + run id)
```

The event module only publishes an event — it has no compile-time dependency on detection, so the
engine can be swapped (e.g. a Kafka-backed `EventProcessor`) without touching ingestion. One failing
rule is caught, logged with the request trace id, and counted as a metric; it never blocks the others.
The engine is gated by `sentinel.detection.enabled`.

## Extensibility

- **New rule type:** add a `DetectionRuleEvaluator` bean returning `Optional<AlertDraft>`; the
  registry discovers it by `type()`. No engine edits. Parameters come from the rule's `config` JSON.
- **New input format:** add an `EventNormalizer` bean (`generic`, `auth`, `web` ship today).
- **Sliding-window state:** behind the `WindowStore` interface (in-memory now, Redis later).
- **Enrichment:** `GeoIpEnricher` (static stub now); failures never block ingestion.

## Backtesting & evaluation

- `POST /api/rules/{id}/backtest` replays stored events through a rule with an optional config
  override in **dry-run** mode (no alerts saved) and records a `backtest_runs` row.
- The **simulator** produces deterministic labeled events; the **evaluation harness**
  (`GET /api/evaluation/detection?runId=`) scores alerts against those labels
  (precision/recall/F1 + mean detection latency). See [detection-rules.md](detection-rules.md).

## Configuration (`sentinel.detection.*`)

| Property | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master flag; off = evaluate nothing |
| `max-linked-events` | `100` | Cap on events linked per alert |
