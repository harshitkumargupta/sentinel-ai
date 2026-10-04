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
