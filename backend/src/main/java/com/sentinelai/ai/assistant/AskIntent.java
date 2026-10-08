package com.sentinelai.ai.assistant;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The fixed questions the incident assistant answers, with the keywords that route free text to them. */
public enum AskIntent {
    WHAT_HAPPENED("What happened?", List.of("what happened", "summary", "summarize", "explain", "overview", "timeline")),
    WHY_RISKY("Why is this risky?", List.of("why", "risk", "severity", "dangerous", "score", "serious")),
    WHAT_TO_DO("What should I do?", List.of("what should", "do next", "recommend", "action", "respond", "fix", "remediat", "mitigat")),
    WHICH_IPS("Which IPs are involved?", List.of("ip", "address", "source", "attacker", "where from", "country")),
    WHICH_MITRE("Which MITRE techniques?", List.of("mitre", "att&ck", "attack technique", "technique", "tactic", "ttp"));

    private final String label;
    private final List<String> keywords;

    AskIntent(String label, List<String> keywords) {
        this.label = label;
        this.keywords = keywords;
    }

    public String label() {
        return label;
    }

    /** Route free text to an intent by keyword; earlier intents win ties. Empty when nothing matches. */
    public static Optional<AskIntent> route(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        String q = question.toLowerCase(Locale.ROOT);
        for (AskIntent intent : values()) {
            if (intent.keywords.stream().anyMatch(q::contains)) {
                return Optional.of(intent);
            }
        }
        return Optional.empty();
    }
}
