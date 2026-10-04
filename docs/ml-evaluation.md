# SentinelAI — ML Evaluation

Model version `20261003210451`, evaluated on a held-out synthetic test set
(1599 events, 399 attacks) including low-and-slow variants the rules miss.

| Approach | Precision | Recall | F1 | ROC-AUC | FP / 1,000 |
|----------|----------:|-------:|---:|--------:|-----------:|
| Rules-only | 1.0 | 0.652 | 0.789 | — | 0.0 |
| Model-only | 1.0 | 0.997 | 0.999 | 1.0 | 0.0 |
| **Hybrid** | 1.0 | 0.997 | 0.999 | 1.0 | 0.0 |

Hybrid (rules OR model) keeps the rules' precision on known patterns while the model recovers
evasive attacks, giving the best recall/F1. Hard rules are never overridden by the model.

_Public-dataset validation:_ run `python validate_public.py` after placing a CICIDS2017 / UNSW-NB15
CSV in `ml/data/` (optional).
