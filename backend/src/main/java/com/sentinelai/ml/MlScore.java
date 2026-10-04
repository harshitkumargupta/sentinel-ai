package com.sentinelai.ml;

import java.util.List;

/** Result of an ML scoring call. */
public record MlScore(int score, String modelVersion, List<TopFeature> topFeatures) {

    public record TopFeature(String name, double value, double shap) {
    }
}
