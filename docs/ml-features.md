# SentinelAI — ML Feature Spec (shared)

Both the Python model (`ml/features.py`) and the Java side (`MlFeatureExtractor`) build feature
vectors from this **single, ordered** spec. Changing it means changing both sides and retraining.

## Entity features (anomaly/attack model) — order matters

| # | name | definition | range |
|---|------|------------|-------|
| 0 | `events_per_min` | events for the entity in the window ÷ window minutes | ≥ 0 |
| 1 | `failed_login_ratio` | FAILED_LOGIN events ÷ total events for the entity | 0–1 |
| 2 | `distinct_users_per_ip` | distinct usernames seen from the source IP in the window | ≥ 0 |
| 3 | `distinct_ips_per_user` | distinct source IPs seen for the user in the window | ≥ 0 |
| 4 | `hour_sin` | sin(2π·hour/24) of the event time (UTC) | −1–1 |
| 5 | `hour_cos` | cos(2π·hour/24) of the event time (UTC) | −1–1 |
| 6 | `geo_change` | 1 if country differs from the entity's previous event, else 0 | 0/1 |
| 7 | `resource_rarity` | 1 − (hits on this resource ÷ max hits), 0 if unknown | 0–1 |
| 8 | `asset_criticality` | event asset criticality ÷ 4 | 0–1 |
| 9 | `honeytoken_flag` | 1 if the event touched a honeytoken, else 0 | 0/1 |

`FEATURE_NAMES` is this list in order. Vectors are plain `double[]`/`list[float]`.

## Admin-action features (admin model / factor)

| # | name | definition | range |
|---|------|------------|-------|
| 0 | `action_sensitivity` | 1 if the action is sensitive/destructive | 0/1 |
| 1 | `burst_count` | admin actions by the actor in the burst window ÷ 10 (clipped 1) | 0–1 |
| 2 | `new_device` | 1 if device not in the admin baseline | 0/1 |
| 3 | `new_ip` | 1 if IP not in the admin baseline | 0/1 |
| 4 | `off_hours` | 1 if outside the admin's typical hours | 0/1 |
| 5 | `baseline_deviation` | fraction of baseline checks that failed | 0–1 |

## Scoring contract

`POST /score` takes `{ "features": [f0..fN], "kind": "entity"|"admin" }` and returns
`{ "score": 0–100, "model_version": "...", "top_features": [{name, value, shap}] }`. The score is the
model's attack probability × 100. No raw PII is sent — only the numeric vector.
