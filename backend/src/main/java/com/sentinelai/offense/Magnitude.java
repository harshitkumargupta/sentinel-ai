package com.sentinelai.offense;

import java.util.List;

/** Offense magnitude with its three 0–10 components, the formula used and why each scored as it did. */
public record Magnitude(int magnitude, int severity, int relevance, int credibility, String formula,
                        List<String> severityReasons, List<String> relevanceReasons,
                        List<String> credibilityReasons) {
}
