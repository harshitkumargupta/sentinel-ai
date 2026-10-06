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
