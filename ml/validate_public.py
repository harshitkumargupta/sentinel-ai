"""Optional: validate the trained model on a public dataset (CICIDS2017 / UNSW-NB15).

Place a CSV in ml/data/ that already has the SentinelAI feature columns (see docs/ml-features.md)
plus a `label` column (0 benign / 1 attack), then run: python validate_public.py data/your.csv
Most raw public datasets need a mapping step to these aggregated features first.
"""
from __future__ import annotations

import sys
from pathlib import Path

import pandas as pd
from sklearn.metrics import f1_score, precision_score, recall_score, roc_auc_score

from features import FEATURE_NAMES
from model_store import load_latest


def main() -> None:
    data_dir = Path(__file__).resolve().parent / "data"
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else next(iter(data_dir.glob("*.csv")), None)
    if path is None or not path.exists():
        print("Place a mapped CSV in ml/data/ (see docs/ml-features.md). Nothing to validate.")
        return
    bundle = load_latest()
    if bundle is None:
        raise SystemExit("No model — run train.py first.")
    df = pd.read_csv(path)
    missing = [c for c in FEATURE_NAMES + ["label"] if c not in df.columns]
    if missing:
        print(f"CSV is missing required columns: {missing}. Map it to the feature spec first.")
        return
    score = bundle["gb_entity"].predict_proba(df[FEATURE_NAMES].to_numpy())[:, 1]
    pred = (score >= bundle["threshold"]).astype(int)
    y = df["label"].to_numpy()
    print({
        "rows": int(len(y)),
        "precision": round(precision_score(y, pred, zero_division=0), 3),
        "recall": round(recall_score(y, pred, zero_division=0), 3),
        "f1": round(f1_score(y, pred, zero_division=0), 3),
        "roc_auc": round(roc_auc_score(y, score), 3),
    })


if __name__ == "__main__":
    main()
