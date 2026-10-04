# AI Evaluation — faithfulness, injection defense, cost

The evaluation harness is extended with AI-specific metrics, computed over completed analyses for an
org and exposed at `GET /api/evaluation/ai` (ANALYST+). It runs identically with the deterministic
`FakeLlmClient` (used in CI) and with a real model (an optional manual run via `AI_PROVIDER=http` +
`LLM_API_KEY`).

## Metrics

- **Faithfulness** — average of (supported claims / total claims) across analyses.
- **Validity mix** — share of analyses that are `VALID` vs `FALLBACK` vs `REJECTED`.
- **Injection-scenario pass rate** — of incidents containing a prompt-injection payload, the share
  that were flagged (a `PROMPT_INJECTION` event raised) and whose output did not follow the
  injection. The guardrails guarantee a flagged case is never followed, so a detected injection is a
  pass; the control under test is detection + non-adoption, verified in `AiPipelineTest`.
- **Median latency / cost per analysis** — from the recorded per-call model latency and token cost.

## Method

- `AiEvaluationTest` (CI, `FakeLlmClient`) seeds brute-force incidents plus one incident carrying a
  prompt-injection user agent, runs an investigation on each, and calls `AiEvaluationService`.
- The injection-defense behavior (flagged, not followed, output schema-validated) is asserted
  directly in `AiPipelineTest#injectionIsFlaggedAndNotFollowed`.

## Results — FakeLlmClient (CI)

The fake model mechanically cites the real evidence, so it is a clean check that the pipeline and
validator accept well-formed, grounded output and that injection is always caught:

| Metric | Value |
|--------|-------|
| Analyses | 3 |
| Avg faithfulness | 1.00 |
| % VALID | 100% |
| % FALLBACK | 0% |
| Injection-scenario pass rate | 100% (1/1 flagged, not followed) |
| Median latency / analysis | ~3 ms (fake, no network) |
| Median cost / analysis | $0.00 (fake) |

> The FakeLlmClient is deterministic, so these numbers are stable in CI. With a real model the
> faithfulness and validity mix depend on the model and prompt; run the harness with
> `AI_PROVIDER=http` and `LLM_API_KEY` set, then read `GET /api/evaluation/ai`. Latency and cost will
> reflect the provider. The key invariants — no unsupported claim is ever accepted (unverifiable
> output becomes FALLBACK) and injection is always flagged and never followed — hold regardless of
> the model, because they are enforced by the evidence validator and the guardrails, not the model.
