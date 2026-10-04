package com.sentinelai.ml;

import java.util.Map;

/** Subset of the model service's /model-info needed for drift checks. */
public record MlModelInfo(String version, Map<String, FeatureStat> trainStats) {

    public record FeatureStat(double mean, double std) {
    }
}
