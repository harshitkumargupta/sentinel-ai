package com.sentinelai.risk.factor;

import com.sentinelai.ml.MlFeatureExtractor;
import com.sentinelai.ml.MlProperties;
import com.sentinelai.ml.MlScore;
import com.sentinelai.ml.MlScoringClient;
import com.sentinelai.risk.FactorResult;
import com.sentinelai.risk.RiskContext;
import com.sentinelai.risk.RiskFactor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adds the ML model's anomaly score as a capped risk factor (it can never dominate hard rules, which
 * contribute independently). Falls back to 0 points when ML is disabled or the service is unavailable.
 * The SHAP top-features become the factor's reason, and the model version is recorded there.
 */
@Component
@RequiredArgsConstructor
public class MlRiskFactor implements RiskFactor {

    private final MlScoringClient client;
    private final MlFeatureExtractor extractor;
    private final MlProperties properties;

    @Override
    public FactorResult score(RiskContext ctx) {
        Optional<MlScore> maybe = client.score(extractor.entityFeatures(ctx.alerts(), ctx.events()), "entity");
        if (maybe.isEmpty()) {
            return FactorResult.none("ml_model", "ML unavailable (rules only)");
        }
        MlScore s = maybe.get();
        int points = Math.min(properties.getWeightCap(),
                (int) Math.round(s.score() / 100.0 * properties.getWeightCap()));
        String top = s.topFeatures().stream().limit(3)
                .map(MlScore.TopFeature::name).collect(Collectors.joining(", "));
        String reason = "model %s anomaly %d%% (%s)".formatted(s.modelVersion(), s.score(),
                top.isEmpty() ? "no top features" : top);
        return new FactorResult("ml_model", points, reason);
    }
}
