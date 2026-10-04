# SentinelAI — ML Scoring Service

Python 3.11 · FastAPI · scikit-learn · SHAP. Anomaly/attack models for entity and admin risk,
served over HTTP to the Java backend (behind the `ml.enabled` flag, with a NoOp fallback).

## Setup

```bash
cd ml
python3.11 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
```

## Train & evaluate

```bash
python train.py                 # synthetic data by default; or --source <java export URL>
python evaluate.py              # writes ../docs/ml-evaluation.md (rules vs model vs hybrid)
python validate_public.py       # optional, if a mapped CSV is in ml/data/
```

`train.py` trains an IsolationForest (unsupervised, on benign) + GradientBoosting (supervised) for
entity risk and a GradientBoosting for admin risk, splitting train/test by seed so the test set
holds out attack draws/variants. Models are versioned in `ml/models/` with a `metrics.json`.

## Serve

```bash
uvicorn app:app --port 8000
```

- `POST /score` — `{ "features": [...], "kind": "entity"|"admin" }` → `{ score 0-100, model_version,
  top_features: [{name, value, shap}] }`. Input is a numeric vector only (no raw PII); ~ms latency.
- `GET /health` — model loaded + version.
- `GET /model-info` — version, feature names, training stats, metrics.

Features are defined once in [../docs/ml-features.md](../docs/ml-features.md) and mirrored by both
`features.py` and the Java `MlFeatureExtractor`. SHAP powers `top_features`; if SHAP is unavailable
the service falls back to model feature importances.

## Tests

```bash
pytest -q
```
