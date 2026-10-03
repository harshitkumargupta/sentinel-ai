package com.sentinelai.ingestion.enrich;

import java.util.Optional;

/**
 * Resolves a source IP to a country. An optional enrichment: implementations must never throw into
 * the ingestion path — a failure returns empty and ingestion continues.
 */
public interface GeoIpEnricher {

    Optional<String> country(String ip);
}
