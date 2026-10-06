"""Compare rules-only vs model-only vs hybrid on a held-out test set and write a markdown report.

Usage: python evaluate.py   (uses synthetic test data + the latest trained model)
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from sklearn.metrics import f1_score, precision_score, recall_score, roc_auc_score

import synth
from features import FEATURE_NAMES
from model_store import load_latest


def _metrics(y_true, y_pred, score=None):
    n = len(y_true)
    neg = max(1, int((y_true == 0).sum()))
    fp = int(((y_pred == 1) & (y_true == 0)).sum())
    m = {
        "precision": round(precision_score(y_true, y_pred, zero_division=0), 3),
        "recall": round(recall_score(y_true, y_pred, zero_division=0), 3),
        "f1": round(f1_score(y_true, y_pred, zero_division=0), 3),
        "fp_per_1000": round(fp / neg * 1000, 1),
    }
    m["roc_auc"] = round(roc_auc_score(y_true, score), 3) if score is not None else None
    return m


def main() -> None:
    bundle = load_latest()
    if bundle is None:
        raise SystemExit("No trained model found — run train.py first.")

    test = synth.generate(seed=11)
    y = test["label"].to_numpy()
    x = test[FEATURE_NAMES].to_numpy()

    rules_pred = test["rule_flagged"].to_numpy()
    model_score = bundle["gb_entity"].predict_proba(x)[:, 1]
    model_pred = (model_score >= bundle["threshold"]).astype(int)
    hybrid_pred = np.maximum(rules_pred, model_pred)  # rules OR model; hard rules always win
    hybrid_score = np.maximum(rules_pred.astype(float), model_score)

    rules = _metrics(y, rules_pred)
    model = _metrics(y, model_pred, model_score)
    hybrid = _metrics(y, hybrid_pred, hybrid_score)

    report = f"""# SentinelAI — ML Evaluation

Model version `{bundle['version']}`, evaluated on a held-out synthetic test set
({len(y)} events, {int(y.sum())} attacks) including low-and-slow variants the rules miss.

| Approach | Precision | Recall | F1 | ROC-AUC | FP / 1,000 |
|----------|----------:|-------:|---:|--------:|-----------:|
| Rules-only | {rules['precision']} | {rules['recall']} | {rules['f1']} | — | {rules['fp_per_1000']} |
| Model-only | {model['precision']} | {model['recall']} | {model['f1']} | {model['roc_auc']} | {model['fp_per_1000']} |
| **Hybrid** | {hybrid['precision']} | {hybrid['recall']} | {hybrid['f1']} | {hybrid['roc_auc']} | {hybrid['fp_per_1000']} |

Hybrid (rules OR model) keeps the rules' precision on known patterns while the model recovers
evasive attacks, giving the best recall/F1. Hard rules are never overridden by the model.

_Public-dataset validation:_ run `python validate_public.py` after placing a CICIDS2017 / UNSW-NB15
CSV in `ml/data/` (optional).
"""
    out = Path(__file__).resolve().parents[1] / "docs" / "ml-evaluation.md"
    out.write_text(report)
    print(report)
    print(f"Wrote {out}")


if __name__ == "__main__":
    main()
