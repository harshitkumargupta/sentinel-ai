import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from fastapi.testclient import TestClient  # noqa: E402

import app as app_module  # noqa: E402
from features import FEATURE_NAMES  # noqa: E402

client = TestClient(app_module.app)


def test_health_ok():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["model_loaded"] is True


def test_model_info():
    r = client.get("/model-info")
    assert r.status_code == 200
    assert r.json()["feature_names"] == FEATURE_NAMES


def test_score_contract():
    r = client.post("/score", json={"features": [0.0] * len(FEATURE_NAMES), "kind": "entity"})
    assert r.status_code == 200
    body = r.json()
    assert 0.0 <= body["score"] <= 100.0
    assert body["model_version"]
    assert len(body["top_features"]) >= 1


def test_wrong_feature_length_is_422():
    r = client.post("/score", json={"features": [0.1, 0.2], "kind": "entity"})
    assert r.status_code == 422


def test_empty_features_rejected():
    r = client.post("/score", json={"features": [], "kind": "entity"})
    assert r.status_code == 422
