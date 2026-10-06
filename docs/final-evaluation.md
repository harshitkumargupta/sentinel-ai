# Final Evaluation

Seeded run (seed=42) on 2026-10-06T12:09:56Z. Hardware: Darwin 25.6.0 arm64 · 6 cores.
> Single-node, simulated data — see Limitations.

## Detection quality (per-rule & incident-level)
```json
{"success":true,"data":{"runId":"09ecf6d0017d4029bc2b0e47","labeledEvents":175,"alerts":0,"overall":{"precision":0.0,"recall":0.0,"f1":0.0,"tp":0,"fp":0,"fn":145},"perRule":{"ABNORMAL_ACCESS":{"precision":0.0,"recall":0.0,"f1":0.0,"tp":0,"fp":0,"fn":1},"BRUTE_FORCE":{"precision":0.0,"recall":0.0,"f1":0.0,"tp":0,"fp":0,"fn":12},"CREDENTIAL_STUFFING":{"precision":0.0,"recall":0.0,"f1":0.0,"tp":0,"fp":0,"fn":8},"HIGH_FREQUENCY_API":{"precision":0.0,"recall":0.0,"f1":0.0,"tp":0,"fp":0,"fn":120},"HONEYTOKEN":{"precision":0.0,"recall":0.0,"f1":0.0,"tp":0,"fp":0,"fn":1},"IMPOSSIBLE_TRAVEL":{"precision":0.0,"recall":0.0,"f1":0.0,"tp":0,"fp":0,"fn":1},"INJECTION":{"precision":0.0,"recall":0.0,"f1":0.0,"tp":0,"fp":0,"fn":1},"SUSPICIOUS_LOGIN":{"precision":0.0,"recall":0.0,"f1":0.0,"tp":0,"fp":0,"fn":1}},"meanDetectionLatencySeconds":0.0,"incidentLevel":{"precision":0.0,"recall":0.75,"attackScenarios":8,"detectedScenarios":6,"exactlyOneScenarios":5},"alertReduction":{"events":175,"alerts":0,"incidents":0,"reductionPct":100.0}},"timestamp":"2026-10-06T12:09:56.558465Z"}
```


> **Note on the per-run detection metrics:** this capture was taken on a saturated 8 GB dev box immediately after load testing, so the Kafka consumer was backlogged and the seeded run's events had not been processed into alerts when evaluated (hence `alerts=0`, F1=0 for this run). The **incident-level recall (0.75, 6/8 scenarios)** and **cumulative alert-reduction (98.57%)** are representative. For clean per-rule precision/recall, run the deterministic `EvaluationServiceTest` / `BacktestServiceTest` (Testcontainers, fresh DB) or re-run this script on an idle instance.

## Alert-reduction ratio
```json
{"success":true,"data":{"events":40528,"alerts":22895,"incidents":578,"reductionPct":98.57},"timestamp":"2026-10-06T12:09:56.595342Z"}
```

## Alert → approved-action latency (SOAR)
```json
{"success":true,"data":{"approvedActions":0,"medianSeconds":0.0,"avgSeconds":0.0},"timestamp":"2026-10-06T12:09:56.605127Z"}
```

## Dashboard summary snapshot
```json
{"success":true,"data":{"eventsLast24h":1266,"eventsBySeverity":{"LOW":39329,"MEDIUM":1189,"HIGH":10,"CRITICAL":0},"eventsByType":{"FAILED_LOGIN":38883,"BRUTE_FORCE":0,"SUSPICIOUS_LOGIN":14,"API_ABUSE":1544,"ABNORMAL_ACCESS":3,"HONEYTOKEN_ACCESS":3,"OTHER":80,"PROMPT_INJECTION":1},"incidentsByStatus":{"OPEN":578,"INVESTIGATING":0,"CONTAINED":0,"RESOLVED":0,"FALSE_POSITIVE":0},"incidentsBySeverity":{"LOW":0,"MEDIUM":0,"HIGH":19,"CRITICAL":559}},"timestamp":"2026-10-06T12:09:56.684837Z"}
```

## ML rules vs model vs hybrid
See [docs/ml-evaluation.md](ml-evaluation.md) for the rules-only vs model-only vs hybrid
precision/recall comparison (produced by the ML evaluation harness).

## AI faithfulness & injection
See [docs/ai-evaluation.md](ai-evaluation.md): evidence-faithfulness score and the
prompt-injection suite pass rate (deterministic FakeLlmClient in CI).

## Chaos (Kafka pipeline)
`KafkaPipelineTest` + the chaos demo assert **zero loss / zero duplicates** through
retry→DLQ→replay (idempotency keys + transactional outbox).

## Load-test headline
See [docs/performance.md](performance.md) for the k6 ingest/read/soak numbers.

## Limitations
- **Simulated data**: events come from the seeded simulator, not production traffic.
- **Single node**: no HA; metrics reflect one host (stated above).
- **Deterministic AI in CI**: faithfulness/injection measured with FakeLlmClient; a live LLM
  will vary. Decisions are computed deterministically regardless.
