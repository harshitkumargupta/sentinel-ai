import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from features import FEATURE_NAMES, hour_to_cyclic, to_vector  # noqa: E402


def test_to_vector_is_deterministic_and_ordered():
    feats = {name: float(i) for i, name in enumerate(FEATURE_NAMES)}
    v1 = to_vector(feats)
    v2 = to_vector(feats)
    assert v1 == v2
    assert v1 == [float(i) for i in range(len(FEATURE_NAMES))]


def test_to_vector_fills_missing_with_zero():
    assert to_vector({"events_per_min": 3.0})[0] == 3.0
    assert to_vector({"events_per_min": 3.0})[1] == 0.0


def test_hour_cyclic_wraps():
    s0, c0 = hour_to_cyclic(0)
    s24, c24 = hour_to_cyclic(24)
    assert round(s0, 6) == round(s24, 6)
    assert round(c0, 6) == round(c24, 6)
