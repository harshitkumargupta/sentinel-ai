# ADR-001: Kafka event-driven ingestion pipeline

- **Status:** Accepted
- **Date:** 2026-10-04
- **Phase:** 11

## Context

Through Phase 10, ingestion was synchronous: `POST /api/events/ingest` persisted the event and ran
the full detection → correlation → notification chain inline, inside the request thread and
transaction. This is simple and great for correctness, but it couples ingest latency to the cost of
detection, offers no back-pressure or buffering under bursts, and makes it hard to scale or fail the
stages independently. We want a path that keeps ingest fast and lets detection/correlation/analytics
/notification run as independent, independently-scalable stages — without losing or duplicating
events, and without a hard dependency on the broker being up.

## Decision

Introduce a **Kafka event-driven pipeline**, gated behind the `kafka.enabled` feature flag.

### Why Kafka
- **Durable, ordered, replayable log.** Partitioned topics keyed by `entity_key` preserve per-entity
  ordering while allowing horizontal scale; offsets make consumption resumable and replayable.
- **Independent consumer groups** let detection, correlation, analytics and notification scale and
  fail independently (one slow/broken stage doesn't stall the others).
- It is the natural substrate for the later streaming/observability phases.
- KRaft mode (no ZooKeeper) keeps the local/dev footprint small.

### Why the outbox pattern
A dual write — commit the event to MySQL *and* publish to Kafka — can lose or duplicate events if
the process dies between the two. Instead, the event row and an `outbox` row are written in **one DB
transaction**; a scheduled **relay** publishes pending rows and marks them sent. If the app crashes
after the commit but before the publish, the relay publishes on restart — **no loss**. The relay
keys each message by the outbox row id, so a re-publish after an ack timeout is deduplicated
downstream rather than processed twice.

### Why consumer-side idempotency
Kafka is at-least-once. Each consumer records `(consumer_group, message_id)` in `processed_messages`
**in the same transaction as its side effect**. A redelivery finds the marker and becomes a no-op;
if the side effect's transaction rolls back, the marker rolls back with it, so a retry can redo the
work cleanly. Message ids are deterministic (derived from the aggregate), so retries and replays map
to the same logical message.

### Why a retry topic + DLQ (not in-place blocking retry)
Blocking retries hold up a partition and let one poison message stall everything behind it. Instead,
a failed message is **acknowledged and routed to `events.retry`** with an incremented attempt; a
single-threaded retry consumer applies **exponential backoff** (sleeping only on the retry
partition) and re-dispatches it to the original handler. After `maxRetries`, it goes to `events.dlq`,
which is drained into the `dlq_messages` table for inspection and one-click replay. Offsets are
committed **manually** after the DB transaction (or after the message is safely moved aside), so a
crash re-delivers rather than drops, and a poison message never blocks its partition.

### Graceful degradation
Everything is behind `kafka.enabled`. With it off — or when the broker is unreachable at ingest time
— the service uses the original synchronous in-process `EventProcessor`, logging the fallback once
with the trace id. Optional dependencies never take down the core API.

## Consequences

- **Pro:** fast, bounded ingest latency; buffering/back-pressure under bursts; independently scalable
  stages; at-least-once with effectively-once side effects; no data loss across crashes; operable DLQ.
- **Con:** detection is now eventually-consistent with ingest (a short end-to-end delay); more moving
  parts (broker, relay, idempotency/outbox/DLQ tables) and more operational surface (lag, DLQ,
  backlog) to monitor — exposed via `/api/admin/pipeline-status` and Micrometer.
- **Tradeoff:** we trade a little end-to-end detection latency for much lower and more predictable
  ingest latency and far better resilience under load. See `docs/performance.md` for measured
  numbers.

## Alternatives considered
- **Synchronous only** — simplest, but couples ingest to detection cost and gives no buffering/scale.
- **Dual write to Kafka without outbox** — simpler, but loses/duplicates events on partial failure.
- **`@RetryableTopic` / `DeadLetterPublishingRecoverer`** — idiomatic, but we wanted explicit named
  topics (`events.retry`/`events.dlq`), manual commits, and a DB-backed DLQ for the admin/replay UX.
