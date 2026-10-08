package com.sentinelai.offense;

import com.sentinelai.incident.domain.IncidentFeedback;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * QRadar-style offense magnitude (SentinelAI's own documented formula):
 * <pre>
 *   severity    = riskScore / 10                       (how bad the activity is)
 *   relevance   = 2 + 4·privileged target + min(4, max asset criticality)   (how much the target matters)
 *   credibility = 3 + 2·(≥2 rule types) + 2·(≥2 log sources or event types)
 *                 + 3·(threat-intel match) + 2·(analyst confirmed); 0 if marked false positive
 *   magnitude   = round((wS·severity + wR·relevance + wC·credibility) / (wS + wR + wC))
 * </pre>
 * Each component is clamped to 0–10; weights come from {@link MagnitudeProperties} (default 3/2/1).
 */
@Component
@RequiredArgsConstructor
public class MagnitudeCalculator {

    private final MagnitudeProperties props;

    public Magnitude calculate(OffenseFacts f) {
        List<String> sevWhy = new ArrayList<>();
        int severity = clamp(Math.round(f.riskScore() / 10f));
        sevWhy.add("risk score " + f.riskScore() + "/100 → " + severity);

        List<String> relWhy = new ArrayList<>();
        int relevance = 2;
        relWhy.add("base 2");
        if (f.privilegedTarget()) {
            relevance += 4;
            relWhy.add("+4 privileged account targeted");
        }
        int crit = Math.min(4, Math.max(0, f.maxAssetCriticality()));
        if (crit > 0) {
            relevance += crit;
            relWhy.add("+" + crit + " asset criticality");
        }
        relevance = clamp(relevance);

        List<String> credWhy = new ArrayList<>();
        int credibility;
        if (f.feedback() == IncidentFeedback.FALSE_POSITIVE) {
            credibility = 0;
            credWhy.add("analyst marked false positive → 0");
        } else {
            credibility = 3;
            credWhy.add("base 3");
            if (f.distinctRuleTypes() >= 2) {
                credibility += 2;
                credWhy.add("+2 corroborated by " + f.distinctRuleTypes() + " rule types");
            }
            if (f.distinctLogSources() >= 2 || f.distinctEventTypes() >= 2) {
                credibility += 2;
                credWhy.add("+2 multiple log sources / event types");
            }
            if (f.threatIntelMatches() > 0) {
                credibility += 3;
                credWhy.add("+3 threat-intel match (" + f.threatIntelMatches() + ")");
            }
            if (f.feedback() == IncidentFeedback.TRUE_POSITIVE) {
                credibility += 2;
                credWhy.add("+2 analyst confirmed true positive");
            }
            credibility = clamp(credibility);
        }

        int wS = props.getSeverityWeight();
        int wR = props.getRelevanceWeight();
        int wC = props.getCredibilityWeight();
        int total = Math.max(1, wS + wR + wC);
        int magnitude = clamp(Math.round((float) (wS * severity + wR * relevance + wC * credibility) / total));
        String formula = "round((%d*%d + %d*%d + %d*%d) / %d) = %d".formatted(
                wS, severity, wR, relevance, wC, credibility, total, magnitude);
        return new Magnitude(magnitude, severity, relevance, credibility, formula, sevWhy, relWhy, credWhy);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(10, v));
    }
}
