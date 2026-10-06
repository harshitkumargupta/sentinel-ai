package com.sentinelai.incident.similarity;

import com.sentinelai.auth.security.AppUserPrincipal;

import java.util.List;

/**
 * Finds past incidents similar to a given one. The default implementation uses feature-vector cosine
 * similarity; an embedding-based implementation can plug in behind this interface later.
 */
public interface SimilarityService {

    List<SimilarIncident> findSimilar(Long incidentId, AppUserPrincipal actor, int limit);
}
