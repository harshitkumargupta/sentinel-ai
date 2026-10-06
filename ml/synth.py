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
