package com.sentinelai.ml;

import com.sentinelai.adminrisk.AdminActionContext;
import com.sentinelai.alert.domain.Alert;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds feature vectors matching docs/ml-features.md (kept in lockstep with Python features.py).
 */
@Component
public class MlFeatureExtractor {

    public static final List<String> FEATURE_NAMES = List.of(
            "events_per_min", "failed_login_ratio", "distinct_users_per_ip", "distinct_ips_per_user",
            "hour_sin", "hour_cos", "geo_change", "resource_rarity", "asset_criticality", "honeytoken_flag");

    public static final List<String> ADMIN_FEATURE_NAMES = List.of(
            "action_sensitivity", "burst_count", "new_device", "new_ip", "off_hours", "baseline_deviation");

    public List<Double> entityFeatures(List<Alert> alerts, List<SecurityEvent> events) {
        if (events.isEmpty()) {
            return FEATURE_NAMES.stream().map(n -> 0.0).toList();
        }
        int total = events.size();
        Instant min = events.stream().map(SecurityEvent::getEventTimestamp).min(Instant::compareTo).orElse(Instant.now());
        Instant max = events.stream().map(SecurityEvent::getEventTimestamp).max(Instant::compareTo).orElse(min);
        double minutes = Math.max(1.0, Duration.between(min, max).toSeconds() / 60.0);

        long failed = events.stream().filter(e -> e.getEventType() == EventType.FAILED_LOGIN).count();
        double eventsPerMin = total / minutes;
        double failedRatio = (double) failed / total;

        double usersPerIp = maxDistinctBy(events, SecurityEvent::getSourceIp, SecurityEvent::getUsername);
        double ipsPerUser = maxDistinctBy(events, SecurityEvent::getUsername, SecurityEvent::getSourceIp);

        int hour = max.atZone(ZoneOffset.UTC).getHour();
        double angle = 2 * Math.PI * hour / 24.0;

        long distinctCountries = events.stream().map(SecurityEvent::getGeoCountry)
                .filter(c -> c != null).distinct().count();
        double geoChange = distinctCountries > 1 ? 1.0 : 0.0;

        double resourceRarity = resourceRarity(events);
        double assetCrit = events.stream().map(e -> e.getAssetCriticality() == null ? 0 : (int) e.getAssetCriticality())
                .max(Integer::compareTo).orElse(0) / 4.0;
        boolean honeytoken = events.stream().anyMatch(SecurityEvent::isHoneytoken)
                || alerts.stream().anyMatch(a -> "HONEYTOKEN".equals(a.getRuleType()));

        return List.of(eventsPerMin, failedRatio, usersPerIp, ipsPerUser,
                Math.sin(angle), Math.cos(angle), geoChange, resourceRarity, assetCrit,
                honeytoken ? 1.0 : 0.0);
    }

    public List<Double> singleEventFeatures(SecurityEvent event) {
        return entityFeatures(List.of(), List.of(event));
    }

    public List<Double> adminFeatures(AdminActionContext ctx, Set<String> sensitiveActions) {
        var b = ctx.baselines();
        boolean newDevice = isNew(b.knownDevices(), ctx.device());
        boolean newIp = isNew(b.knownIps(), ctx.ipAddress());
        int hour = ctx.at().atZone(ZoneOffset.UTC).getHour();
        boolean offHours = b.typicalHours() != null && !b.typicalHours().isEmpty() && !b.typicalHours().contains(hour);

        int checks = 0;
        int failedChecks = 0;
        if (b.knownCountries() != null && !b.knownCountries().isEmpty()) {
            checks++;
            if (isNew(b.knownCountries(), ctx.country())) failedChecks++;
        }
        if (b.knownIps() != null && !b.knownIps().isEmpty()) { checks++; if (newIp) failedChecks++; }
        if (b.knownDevices() != null && !b.knownDevices().isEmpty()) { checks++; if (newDevice) failedChecks++; }
        double deviation = checks == 0 ? 0.0 : (double) failedChecks / checks;

        return List.of(
                sensitiveActions.contains(ctx.action()) ? 1.0 : 0.0,
                Math.min(1.0, ctx.recentActionCount() / 10.0),
                newDevice ? 1.0 : 0.0,
                newIp ? 1.0 : 0.0,
                offHours ? 1.0 : 0.0,
                deviation);
    }

    private boolean isNew(Set<String> baseline, String value) {
        return baseline != null && !baseline.isEmpty() && value != null && !baseline.contains(value);
    }

    private double maxDistinctBy(List<SecurityEvent> events,
                                 java.util.function.Function<SecurityEvent, String> key,
                                 java.util.function.Function<SecurityEvent, String> value) {
        Map<String, Long> counts = events.stream()
                .filter(e -> key.apply(e) != null && value.apply(e) != null)
                .collect(Collectors.groupingBy(key, Collectors.mapping(value,
                        Collectors.collectingAndThen(Collectors.toSet(), s -> (long) s.size()))));
        return counts.values().stream().max(Long::compareTo).orElse(0L).doubleValue();
    }

    private double resourceRarity(List<SecurityEvent> events) {
        Map<String, Long> byResource = events.stream().filter(e -> e.getResource() != null)
                .collect(Collectors.groupingBy(SecurityEvent::getResource, Collectors.counting()));
        if (byResource.isEmpty()) {
            return 0.0;
        }
        long maxHits = byResource.values().stream().max(Long::compareTo).orElse(1L);
        return 1.0 - ((double) maxHits / events.size());
    }
}
