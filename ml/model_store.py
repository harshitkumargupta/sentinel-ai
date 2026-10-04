"""Versioned model persistence."""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict

import joblib

MODELS_DIR = Path(__file__).resolve().parent / "models"
MODELS_DIR.mkdir(exist_ok=True)
_LATEST = MODELS_DIR / "latest.txt"


def save_bundle(bundle: Dict[str, Any], version: str, metrics: Dict[str, Any]) -> Path:
    path = MODELS_DIR / f"model_{version}.joblib"
    joblib.dump(bundle, path)
    (MODELS_DIR / f"metrics_{version}.json").write_text(json.dumps(metrics, indent=2))
    (MODELS_DIR / "metrics.json").write_text(json.dumps(metrics, indent=2))
    _LATEST.write_text(version)
    return path


def latest_version() -> str | None:
    return _LATEST.read_text().strip() if _LATEST.exists() else None


def load_latest() -> Dict[str, Any] | None:
    v = latest_version()
    if not v:
        return None
    path = MODELS_DIR / f"model_{v}.joblib"
    return joblib.load(path) if path.exists() else None
