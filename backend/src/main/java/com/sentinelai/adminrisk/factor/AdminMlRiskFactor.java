package com.sentinelai.adminrisk.factor;

import com.sentinelai.adminrisk.AdminActionContext;
import com.sentinelai.adminrisk.AdminActionRiskFactor;
import com.sentinelai.adminrisk.AdminRiskProperties;
import com.sentinelai.ml.MlFeatureExtractor;
import com.sentinelai.ml.MlProperties;
import com.sentinelai.ml.MlScore;
import com.sentinelai.ml.MlScoringClient;
import com.sentinelai.risk.FactorResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** The same ML model plugged into the admin-risk engine (capped; graceful fallback). */
@Component
@RequiredArgsConstructor
public class AdminMlRiskFactor implements AdminActionRiskFactor {

    private final MlScoringClient client;
    private final MlFeatureExtractor extractor;
    private final MlProperties mlProperties;

    @Override
    public FactorResult score(AdminActionContext ctx, AdminRiskProperties props) {
        Optional<MlScore> maybe = client.score(
                extractor.adminFeatures(ctx, props.getSensitiveActions()), "admin");
        if (maybe.isEmpty()) {
            return FactorResult.none("ml_model", "ML unavailable (rules only)");
        }
        MlScore s = maybe.get();
        int points = Math.min(mlProperties.getWeightCap(),
                (int) Math.round(s.score() / 100.0 * mlProperties.getWeightCap()));
        return new FactorResult("ml_model", points,
                "model %s anomaly %d%%".formatted(s.modelVersion(), s.score()));
    }
}
