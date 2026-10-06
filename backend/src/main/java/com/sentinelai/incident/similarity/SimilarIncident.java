package com.sentinelai.incident.similarity;

import java.util.List;

/**
 * A past incident similar to the queried one, with the similarity score, the features they share,
 * and how it was resolved (status, feedback, and any response actions that were executed).
 */
public record SimilarIncident(
        Long incidentId,
        String title,
        String severity,
        String status,
        String feedback,
        double score,
        List<String> sharedFeatures,
        List<String> actionsTaken,
        boolean actionResolved) {
}
