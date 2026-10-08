# ML Explanation

## 1. Problem The ML/AI Solves
The ML service estimates risk for security entities and admin actions. It plugs into the backend through ML feature extraction/client code and returns a risk score plus top features. Backend AI code separately supports incident investigation, natural-language search, prompt protection, and evidence validation.

## 2. Every ML/AI-Related File
| File path | Layer | Purpose | Used by |
|---|---|---|---|
| `ml/.dockerignore` | ml | config file | Project tooling/runtime |
| `ml/.gitignore` | ml | config file | Project tooling/runtime |
| `ml/Dockerfile` | ml | config file | Project tooling/runtime |
| `ml/README.md` | ml | md file | Project tooling/runtime |
| `ml/app.py` | ml | ScoreRequest, TopFeature, ScoreResponse, _warm_explainers, _model_and_names, _top_features, score, health, model_info | Python runtime/tests or HTTP callers |
| `ml/data/.gitkeep` | ml | config file | Project tooling/runtime |
| `ml/evaluate.py` | ml | _metrics, main | Python runtime/tests or HTTP callers |
| `ml/features.py` | ml | hour_to_cyclic, to_vector | Python runtime/tests or HTTP callers |
| `ml/model_store.py` | ml | save_bundle, latest_version, load_latest | Python runtime/tests or HTTP callers |
| `ml/models/latest.txt` | ml | txt file | Project tooling/runtime |
| `ml/models/metrics.json` | ml | json file | Project tooling/runtime |
| `ml/models/metrics_20261003210451.json` | ml | json file | Project tooling/runtime |
| `ml/models/model_20261003210451.joblib` | ml | joblib file | Project tooling/runtime |
| `ml/requirements.txt` | ml | txt file | Project tooling/runtime |
| `ml/synth.py` | ml | _row, rule_flagged, generate, _admin_row, generate_admin | Python runtime/tests or HTTP callers |
| `ml/tests/conftest.py` | ml | conftest | Python runtime/tests or HTTP callers |
| `ml/tests/test_api.py` | ml | test_health_ok, test_model_info, test_score_contract, test_wrong_feature_length_is_422, test_empty_features_rejected | Python runtime/tests or HTTP callers |
| `ml/tests/test_features.py` | ml | test_to_vector_is_deterministic_and_ordered, test_to_vector_fills_missing_with_zero, test_hour_cyclic_wraps | Python runtime/tests or HTTP callers |
| `ml/train.py` | ml | _fit_classifier, _load_entity, main | Python runtime/tests or HTTP callers |
| `ml/validate_public.py` | ml | main | Python runtime/tests or HTTP callers |

Backend AI/ML files are under `backend/src/main/java/com/sentinelai/ml/**`, `backend/src/main/java/com/sentinelai/ai/**`, and prompt templates in `backend/src/main/resources/prompts/*.txt`.

## 3. Dataset
Default data is synthetic from `ml/synth.py`. Optional data can come from the Java `/api/ml/training-data` export via `python train.py --source <url>`. Entity features: `events_per_min`, `failed_login_ratio`, `distinct_users_per_ip`, `distinct_ips_per_user`, `hour_sin`, `hour_cos`, `geo_change`, `resource_rarity`, `asset_criticality`, `honeytoken_flag`. Admin features: `action_sensitivity`, `burst_count`, `new_device`, `new_ip`, `off_hours`, `baseline_deviation`. Defaults: entity `1200` benign and `400` attack rows per generated set; admin `800` benign and `300` attack rows.

## 4. Algorithms/Models Used
- `IsolationForest(random_state=0, contamination=0.2)` for benign-trained anomaly detection.
- `GradientBoostingClassifier(random_state=0)` for entity risk.
- `GradientBoostingClassifier(random_state=0)` for admin risk.
- Classification threshold: `0.5`.
- SHAP `TreeExplainer` for top-feature explanations, with feature-importance fallback.

## 5. Training Pipeline, Actual Code
### `ml/features.py`
```python
"""Shared feature spec for SentinelAI's ML models.

Keep in lockstep with docs/ml-features.md and the Java MlFeatureExtractor.
"""
from __future__ import annotations

import math
from typing import Dict, List

FEATURE_NAMES: List[str] = [
    "events_per_min",
    "failed_login_ratio",
    "distinct_users_per_ip",
    "distinct_ips_per_user",
    "hour_sin",
    "hour_cos",
    "geo_change",
    "resource_rarity",
    "asset_criticality",
    "honeytoken_flag",
]

ADMIN_FEATURE_NAMES: List[str] = [
    "action_sensitivity",
    "burst_count",
    "new_device",
    "new_ip",
    "off_hours",
    "baseline_deviation",
]


def hour_to_cyclic(hour: int) -> tuple[float, float]:
    """Encode hour-of-day (0-23) as (sin, cos) so 23:00 and 00:00 are close."""
    angle = 2.0 * math.pi * (hour % 24) / 24.0
    return math.sin(angle), math.cos(angle)


def to_vector(features: Dict[str, float], names: List[str] = FEATURE_NAMES) -> List[float]:
    """Project a feature dict onto the canonical ordered vector (missing -> 0.0).

    Deterministic: the same dict always yields the same vector.
    """
    return [float(features.get(name, 0.0)) for name in names]

```
Explanation: imports helpers; defines canonical entity/admin feature names; encodes hour as cyclic sine/cosine; projects dicts into ordered numeric vectors with missing values as `0.0`.

### `ml/synth.py`
```python
"""Deterministic, noisy synthetic training data mirroring the simulator's scenarios.

Lets train/evaluate run self-contained; the same vectors can also come from the Java export
(`/api/ml/training-data`). `rule_flagged` mirrors the hard detection thresholds so the evaluation
can compare rules-only vs model vs hybrid — including low-and-slow attacks that evade the rules.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from features import FEATURE_NAMES, hour_to_cyclic


def _row(rng, scenario, label, **over):
    hour = over.pop("hour", int(rng.integers(0, 24)))
    s, c = hour_to_cyclic(hour)
    base = {
        "events_per_min": max(0.0, rng.normal(1.0, 0.6)),
        "failed_login_ratio": min(1.0, max(0.0, rng.normal(0.05, 0.05))),
        "distinct_users_per_ip": max(0.0, rng.normal(1.0, 0.4)),
        "distinct_ips_per_user": max(0.0, rng.normal(1.0, 0.3)),
        "hour_sin": s,
        "hour_cos": c,
        "geo_change": 0.0,
        "resource_rarity": min(1.0, max(0.0, rng.normal(0.1, 0.1))),
        "asset_criticality": min(1.0, max(0.0, rng.normal(0.2, 0.15))),
        "honeytoken_flag": 0.0,
    }
    base.update(over)
    base["scenario"] = scenario
    base["label"] = label
    return base


def rule_flagged(r) -> int:
    """Mirror the hard detection rules (thresholds on the aggregated features)."""
    if r["honeytoken_flag"] >= 1:
        return 1
    if r["failed_login_ratio"] > 0.8 and r["events_per_min"] > 5:   # brute force
        return 1
    if r["distinct_users_per_ip"] >= 5:                              # credential stuffing
        return 1
    if r["events_per_min"] > 50:                                     # api abuse
        return 1
    if r["geo_change"] >= 1 and r["distinct_ips_per_user"] >= 2:     # impossible travel
        return 1
    return 0


def generate(seed: int = 42, n_benign: int = 1200, n_attack: int = 400) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    rows = []
    for _ in range(n_benign):
        rows.append(_row(rng, "normal", 0))

    # Attack mix, including evasive low-and-slow variants that the rules miss.
    per = max(1, n_attack // 7)
    for _ in range(per):
        rows.append(_row(rng, "brute_force_loud", 1,
                         failed_login_ratio=min(1, rng.normal(0.92, 0.04)),
                         events_per_min=rng.normal(9, 2), distinct_ips_per_user=1.0))
    for _ in range(per):
        rows.append(_row(rng, "brute_force_stealth", 1,  # below rule thresholds
                         failed_login_ratio=min(1, rng.normal(0.68, 0.06)),
                         events_per_min=rng.normal(3.0, 0.8)))
    for _ in range(per):
        rows.append(_row(rng, "credential_stuffing", 1,
                         distinct_users_per_ip=rng.normal(9, 3),
                         failed_login_ratio=min(1, rng.normal(0.7, 0.1))))
    for _ in range(per):
        rows.append(_row(rng, "api_abuse", 1,
                         events_per_min=rng.normal(90, 30), resource_rarity=rng.normal(0.6, 0.2)))
    for _ in range(per):
        rows.append(_row(rng, "suspicious_login", 1, hour=int(rng.integers(0, 5)),
                         geo_change=1.0))
    for _ in range(per):
        rows.append(_row(rng, "impossible_travel", 1, geo_change=1.0,
                         distinct_ips_per_user=rng.normal(2.5, 0.6)))
    for _ in range(per):
        rows.append(_row(rng, "honeytoken", 1, honeytoken_flag=1.0,
                         asset_criticality=rng.normal(0.8, 0.1)))

    df = pd.DataFrame(rows)
    for col in FEATURE_NAMES:
        df[col] = df[col].astype(float).clip(lower=-1.0)
    df["rule_flagged"] = df.apply(rule_flagged, axis=1).astype(int)
    return df.sample(frac=1.0, random_state=seed).reset_index(drop=True)


def _admin_row(rng, label, **over):
    base = {
        "action_sensitivity": float(rng.integers(0, 2)),
        "burst_count": min(1.0, max(0.0, rng.normal(0.1, 0.1))),
        "new_device": 0.0,
        "new_ip": 0.0,
        "off_hours": float(rng.integers(0, 2)) if rng.random() < 0.2 else 0.0,
        "baseline_deviation": min(1.0, max(0.0, rng.normal(0.1, 0.1))),
    }
    base.update(over)
    base["label"] = label
    return base


def generate_admin(seed: int = 42, n_benign: int = 800, n_attack: int = 300) -> pd.DataFrame:
    """Synthetic admin-action rows: benign vs insider/compromised (burst, new origin, off-hours)."""
    rng = np.random.default_rng(seed + 1)
    rows = [_admin_row(rng, 0) for _ in range(n_benign)]
    per = max(1, n_attack // 2)
    for _ in range(per):  # insider mass action
        rows.append(_admin_row(rng, 1, action_sensitivity=1.0,
                               burst_count=min(1, rng.normal(0.8, 0.15)),
                               off_hours=1.0, baseline_deviation=min(1, rng.normal(0.6, 0.15))))
    for _ in range(n_attack - per):  # stolen session from new origin
        rows.append(_admin_row(rng, 1, action_sensitivity=1.0, new_device=1.0, new_ip=1.0,
                               off_hours=float(rng.integers(0, 2)),
                               baseline_deviation=min(1, rng.normal(0.7, 0.1))))
    return pd.DataFrame(rows).sample(frac=1.0, random_state=seed).reset_index(drop=True)

```
Explanation: creates deterministic benign and attack-like rows, labels scenarios, mirrors rule thresholds in `rule_flagged`, clips/shuffles feature values, and creates admin-risk rows for insider and stolen-session scenarios.

### `ml/train.py`
```python
"""Train SentinelAI anomaly/attack models.

Trains an IsolationForest (unsupervised, on benign) and a GradientBoosting classifier (supervised)
for entity-risk, plus a GradientBoosting classifier for admin-risk. Data is synthetic by default
(deterministic + noisy) or pulled from the Java export with --source. Train/test are split by seed so
the test set contains held-out attack draws/variants. Saves a versioned bundle + metrics.json.

Usage: python train.py [--source URL] [--version V]
"""
from __future__ import annotations

import argparse
import datetime as dt

import numpy as np
import pandas as pd
import requests
from sklearn.ensemble import GradientBoostingClassifier, IsolationForest
from sklearn.metrics import f1_score, precision_score, recall_score, roc_auc_score

import synth
from features import ADMIN_FEATURE_NAMES, FEATURE_NAMES
from model_store import save_bundle


def _fit_classifier(train: pd.DataFrame, test: pd.DataFrame, names):
    xtr, ytr = train[names].to_numpy(), train["label"].to_numpy()
    xte, yte = test[names].to_numpy(), test["label"].to_numpy()
    gb = GradientBoostingClassifier(random_state=0).fit(xtr, ytr)
    proba = gb.predict_proba(xte)[:, 1]
    pred = (proba >= 0.5).astype(int)
    metrics = {
        "precision": round(precision_score(yte, pred, zero_division=0), 3),
        "recall": round(recall_score(yte, pred, zero_division=0), 3),
        "f1": round(f1_score(yte, pred, zero_division=0), 3),
        "roc_auc": round(roc_auc_score(yte, proba), 3),
        "test_size": int(len(yte)),
    }
    return gb, metrics


def _load_entity(source: str | None) -> tuple[pd.DataFrame, pd.DataFrame]:
    if source:
        rows = requests.get(source, timeout=30).json()["rows"]
        df = pd.DataFrame([{**dict(zip(FEATURE_NAMES, r["features"])),
                            "label": r["label"], "rule_flagged": r.get("ruleFlagged", 0)} for r in rows])
        # split 70/30 deterministically
        cut = int(len(df) * 0.7)
        return df.iloc[:cut].reset_index(drop=True), df.iloc[cut:].reset_index(drop=True)
    return synth.generate(seed=42), synth.generate(seed=7)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default=None, help="URL of the Java /api/ml/training-data export")
    ap.add_argument("--version", default=dt.datetime.utcnow().strftime("%Y%m%d%H%M%S"))
    args = ap.parse_args()

    train_e, test_e = _load_entity(args.source)
    iso = IsolationForest(random_state=0, contamination=0.2).fit(train_e[train_e.label == 0][FEATURE_NAMES])
    gb_e, m_entity = _fit_classifier(train_e, test_e, FEATURE_NAMES)

    train_a, test_a = synth.generate_admin(seed=42), synth.generate_admin(seed=7)
    gb_a, m_admin = _fit_classifier(train_a, test_a, ADMIN_FEATURE_NAMES)

    train_stats = {n: {"mean": float(train_e[n].mean()), "std": float(train_e[n].std() or 1.0)}
                   for n in FEATURE_NAMES}

    bundle = {
        "version": args.version,
        "gb_entity": gb_e, "iso_entity": iso, "gb_admin": gb_a,
        "feature_names": FEATURE_NAMES, "admin_feature_names": ADMIN_FEATURE_NAMES,
        "train_stats": train_stats, "threshold": 0.5,
    }
    metrics = {"version": args.version, "entity": m_entity, "admin": m_admin,
               "trained_at": dt.datetime.utcnow().isoformat() + "Z"}
    path = save_bundle(bundle, args.version, metrics)
    print(f"Saved {path}")
    print(f"entity: {m_entity}")
    print(f"admin:  {m_admin}")


if __name__ == "__main__":
    main()

```
Explanation: parses CLI args; loads backend-export or synthetic data; trains Isolation Forest on benign entity rows; trains Gradient Boosting for entity and admin labels; computes precision/recall/F1/ROC-AUC; stores model bundle, feature names, train stats, threshold, and metrics.

## 6. Evaluation
```json
{
  "version": "20261003210451",
  "entity": {
    "precision": 1.0,
    "recall": 0.997,
    "f1": 0.999,
    "roc_auc": 1.0,
    "test_size": 1599
  },
  "admin": {
    "precision": 0.98,
    "recall": 0.997,
    "f1": 0.988,
    "roc_auc": 0.993,
    "test_size": 1100
  },
  "trained_at": "2026-10-03T21:04:51.357868Z"
}
```
`ml/evaluate.py` compares rules-only, model-only, and hybrid predictions and writes `docs/ml-evaluation.md`.

## 7. Inference Pipeline, Actual Code
```python
"""SentinelAI ML scoring service (FastAPI).

Endpoints: POST /score, GET /health, GET /model-info. Input is a numeric feature vector only
(no raw PII). Explanations use SHAP when available, else model feature importances.
"""
from __future__ import annotations

import json
from typing import List, Literal, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from model_store import MODELS_DIR, load_latest

try:
    import shap  # optional
    _HAS_SHAP = True
except Exception:  # pragma: no cover - shap is best-effort
    _HAS_SHAP = False

app = FastAPI(title="SentinelAI ML", version="1.0")
_BUNDLE = load_latest()
_EXPLAINERS: dict = {}


def _warm_explainers() -> None:
    """Pre-build SHAP explainers at startup so /score stays within its latency budget."""
    if not (_HAS_SHAP and _BUNDLE):
        return
    for key in ("gb_entity", "gb_admin"):
        model = _BUNDLE.get(key)
        try:
            _EXPLAINERS[id(model)] = shap.TreeExplainer(model)
        except Exception:
            pass


_warm_explainers()


class ScoreRequest(BaseModel):
    features: List[float] = Field(..., min_length=1, max_length=64)
    kind: Literal["entity", "admin"] = "entity"


class TopFeature(BaseModel):
    name: str
    value: float
    shap: float


class ScoreResponse(BaseModel):
    score: float
    model_version: str
    top_features: List[TopFeature]


def _model_and_names(kind: str):
    if _BUNDLE is None:
        raise HTTPException(status_code=503, detail="No model loaded")
    if kind == "admin":
        return _BUNDLE["gb_admin"], _BUNDLE["admin_feature_names"]
    return _BUNDLE["gb_entity"], _BUNDLE["feature_names"]


def _top_features(model, names, vector) -> List[TopFeature]:
    contributions: Optional[list] = None
    if _HAS_SHAP:
        try:
            explainer = _EXPLAINERS.get(id(model)) or shap.TreeExplainer(model)
            _EXPLAINERS[id(model)] = explainer
            sv = explainer.shap_values([vector])
            row = sv[1][0] if isinstance(sv, list) else sv[0]
            contributions = [float(x) for x in row]
        except Exception:
            contributions = None
    if contributions is None:  # fallback: importance * value
        imp = getattr(model, "feature_importances_", [0.0] * len(names))
        contributions = [float(imp[i]) * float(vector[i]) for i in range(len(names))]
    pairs = sorted(
        ({"name": names[i], "value": float(vector[i]), "shap": round(contributions[i], 4)}
         for i in range(len(names))),
        key=lambda d: abs(d["shap"]), reverse=True)
    return [TopFeature(**p) for p in pairs[:5]]


@app.post("/score", response_model=ScoreResponse)
def score(req: ScoreRequest) -> ScoreResponse:
    model, names = _model_and_names(req.kind)
    if len(req.features) != len(names):
        raise HTTPException(status_code=422,
                            detail=f"expected {len(names)} features for kind={req.kind}")
    proba = float(model.predict_proba([req.features])[0][1])
    return ScoreResponse(score=round(proba * 100.0, 2),
                         model_version=_BUNDLE["version"],
                         top_features=_top_features(model, names, req.features))


@app.get("/health")
def health() -> dict:
    return {"status": "UP" if _BUNDLE else "NO_MODEL",
            "model_loaded": _BUNDLE is not None,
            "version": _BUNDLE["version"] if _BUNDLE else None,
            "shap": _HAS_SHAP}


@app.get("/model-info")
def model_info() -> dict:
    if _BUNDLE is None:
        raise HTTPException(status_code=503, detail="No model loaded")
    metrics_path = MODELS_DIR / "metrics.json"
    metrics = json.loads(metrics_path.read_text()) if metrics_path.exists() else {}
    return {"version": _BUNDLE["version"],
            "feature_names": _BUNDLE["feature_names"],
            "admin_feature_names": _BUNDLE["admin_feature_names"],
            "train_stats": _BUNDLE["train_stats"],
            "metrics": metrics}

```
Explanation: loads latest model bundle; warms SHAP explainers; validates `/score` requests; selects entity/admin model; checks vector length; predicts probability; converts it to score `0-100`; returns model version and top five features; `/health` and `/model-info` expose readiness and metadata.

## 8. Integration
Backend calls ML over HTTP at `ML_BASE_URL`, default `http://localhost:8000`. `POST /score` accepts a `features` array and `kind` of `entity` or `admin`; it returns a score, model version, and top features. Frontend does not call ML directly.

## 9. Dependencies, Model Locations, Retrain/Rerun
Dependencies:
- fastapi==0.111.0
- uvicorn[standard]==0.30.1
- scikit-learn==1.5.1
- pandas==2.2.2
- numpy==1.26.4
- joblib==1.4.2
- pydantic==2.8.2
- requests==2.32.3
- pytest==8.2.2
- httpx==0.27.0
- shap==0.46.0

Model files: `ml/models/latest.txt`, `ml/models/model_20261003210451.joblib`, `ml/models/metrics.json`, `ml/models/metrics_20261003210451.json`.

```bash
cd ml
python train.py
python train.py --source http://localhost:8080/api/ml/training-data
uvicorn app:app --port 8000
python evaluate.py
```

## 10. Limitations And Possible Improvements
Synthetic data may not match production; no public CSV was found in `ml/data`; Isolation Forest is trained but `/score` uses Gradient Boosting probabilities; SHAP is optional; hyperparameters are mostly defaults; real time-based validation and calibration would improve reliability.

## 11. Viva-Style Q&A
1. What does ML do here? It scores risky behavior from numeric security features.
2. Why use synthetic data? It enables a privacy-safe demo without real logs.
3. What model is supervised? Gradient Boosting.
4. What model is unsupervised? Isolation Forest.
5. Why use sine/cosine for hour? To represent daily time as circular.
6. What is the inference endpoint? `POST /score`.
7. What is returned by inference? Score, model version, and top features.
8. Does frontend call ML? No, backend calls ML.
9. What happens without SHAP? Feature-importance fallback is used.
10. How to retrain? Run `python train.py`, optionally with backend export as `--source`.
