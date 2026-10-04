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
