package com.sentinelai.ml;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Fallback used when ML is disabled (or no HTTP client is configured): always empty. */
@Component
@ConditionalOnProperty(prefix = "sentinel.ml", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpMlScoringClient implements MlScoringClient {

    @Override
    public Optional<MlScore> score(List<Double> features, String kind) {
        return Optional.empty();
    }

    @Override
    public Optional<MlModelInfo> modelInfo() {
        return Optional.empty();
    }
}
