# ADR-002: AI investigation guardrails

- **Status:** Accepted
- **Date:** 2026-10-04
- **Phase:** 12

## Context

We want AI to help analysts triage incidents — summarize the attack, hypothesize root cause, and
suggest responses — and to allow natural-language event search. But an LLM in a security product is
itself an attack surface: it can hallucinate findings, be manipulated by attacker-controlled log
data (prompt injection), leak data to the provider, run up cost, or (if wired to tools) take unsafe
actions. The AI must be **useful but untrusted**, and the core platform must never depend on it.

## Decision

Add an AI investigation layer behind `ai.enabled`, built around four principles.

### 1. Evidence-grounded, never trusted
The model returns a strict JSON object, and an **EvidenceValidator** is the gate: every claim must
cite event ids that belong to this incident and org; recommendation actions are allow-listed and
their targets must appear in the evidence; the schema is enforced. A **faithfulness score**
(supported/total claims) is recorded. Invalid output gets one repair retry, then a deterministic
fallback. This is what makes the feature safe to show analysts: the model can only describe what the
evidence supports.

### 2. The model cannot act
The LLM has no tools and no execution path. Its output can only create **PROPOSED** playbook actions,
which a human approves; execution is a separate phase. Any instruction-style text in the output is
simply ignored by the schema validator.

### 3. Defense against prompt injection
Log fields are attacker-controlled, so: untrusted data is wrapped in clearly delimited blocks, the
system prompt declares the data is not instructions and never contains untrusted text, instruction
patterns and control characters are stripped and fields truncated, and an injection detector raises
its own `PROMPT_INJECTION` security event and flags the incident. Crucially, the output is
schema-validated against the evidence **regardless** of what the data says, so a successful "jailbreak"
of the wording still cannot produce an unsupported claim or an unlisted action.

### 4. Graceful degradation and cost control
The API key comes only from `LLM_API_KEY` (never committed/logged). A daily token/cost budget and a
per-org quota gate spend (exceeded → fallback, not an error), with a circuit breaker and
retries-with-backoff. With `ai.enabled=false` or the model down, every feature returns a
deterministic result. Re-investigation is idempotent via a context hash, and the endpoint is
rate-limited per org.

### Natural-language search
The LLM only ever emits a **structured, allow-listed filter** (fields: ip, user, type, severity,
country, time range, limit). We never accept SQL or raw expressions; the validated filter is capped
and run through the existing parameterized event query, and the interpreted filter is shown to the
user as chips. Unparseable input yields a clear error.

## Consequences

- **Pro:** analysts get fast, cited summaries and recommendations they can trust and act on with one
  click; the platform is resilient to model outage, injection and hallucination; cost is bounded.
- **Con:** more moving parts (budget, circuit breaker, validator, prompt templates) and some genuine
  model findings may be downgraded to FALLBACK when they can't be tied to evidence — we accept that
  trade in favor of never surfacing an unsupported claim.
- **Tradeoff:** we prefer a deterministic, evidence-backed fallback over a richer but unverifiable
  model answer.

## Alternatives considered
- **Four separate agents (threat/correlation/root-cause/response)** — more calls, more cost, and a
  harder validation story. We use one structured response covering the three stages, validated once.
- **Trust the model and post-filter lightly** — rejected; unsupported claims in a SOC are dangerous.
- **Let the model call tools / act** — rejected; actions stay human-approved and allow-listed.
