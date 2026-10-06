package com.sentinelai.dashboard.dto;

/**
 * Aggregated attack origin for the AttackGlobe: a country, how many events came from it, and the
 * most common event type. Coordinates are mapped client-side from the country code.
 */
public record GeoFlowItem(String country, long count, String topType) {
}
