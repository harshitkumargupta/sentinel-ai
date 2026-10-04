"""Ensure a model exists before API tests import the app (train a small one if needed)."""
import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from sklearn.ensemble import GradientBoostingClassifier, IsolationForest  # noqa: E402

import synth  # noqa: E402
from features import ADMIN_FEATURE_NAMES, FEATURE_NAMES  # noqa: E402
import model_store  # noqa: E402

if model_store.load_latest() is None:
    e = synth.generate(seed=1, n_benign=200, n_attack=100)
    a = synth.generate_admin(seed=1, n_benign=200, n_attack=80)
    bundle = {
        "version": "test",
        "gb_entity": GradientBoostingClassifier(random_state=0).fit(e[FEATURE_NAMES], e["label"]),
        "iso_entity": IsolationForest(random_state=0).fit(e[e.label == 0][FEATURE_NAMES]),
        "gb_admin": GradientBoostingClassifier(random_state=0).fit(a[ADMIN_FEATURE_NAMES], a["label"]),
        "feature_names": FEATURE_NAMES,
        "admin_feature_names": ADMIN_FEATURE_NAMES,
        "train_stats": {n: {"mean": 0.0, "std": 1.0} for n in FEATURE_NAMES},
        "threshold": 0.5,
    }
    model_store.save_bundle(bundle, "test", {"version": "test", "entity": {}, "admin": {}})
