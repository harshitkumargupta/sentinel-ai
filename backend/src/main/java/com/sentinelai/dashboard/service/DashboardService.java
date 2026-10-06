package com.sentinelai.dashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.cache.CacheService;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.dashboard.dto.AlertReductionResponse;
import com.sentinelai.dashboard.dto.DashboardSummary;
import com.sentinelai.dashboard.dto.MitreCoverageItem;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.redis.RedisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SecurityEventRepository securityEventRepository;
    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;
    private final DetectionRuleRepository ruleRepository;
    private final CacheService cache;
    private final RedisProperties redisProperties;

    private String key(Long org, String name) {
        return "dash:org:" + org + ":site:all:" + name;
    }

    public DashboardSummary summary(AppUserPrincipal actor) {
        return cache.getOrLoad(key(actor.getOrgId(), "summary"),
                new TypeReference<>() {}, redisProperties.getCacheTtlSeconds(), () -> computeSummary(actor));
    }

    public AlertReductionResponse alertReduction(AppUserPrincipal actor) {
        return cache.getOrLoad(key(actor.getOrgId(), "alert-reduction"),
                new TypeReference<>() {}, redisProperties.getCacheTtlSeconds(), () -> computeAlertReduction(actor));
    }

    public List<MitreCoverageItem> mitreCoverage(AppUserPrincipal actor) {
        return cache.getOrLoad(key(actor.getOrgId(), "mitre-coverage"),
                new TypeReference<>() {}, redisProperties.getCacheTtlSeconds(), () -> computeMitreCoverage(actor));
    }

    public List<com.sentinelai.dashboard.dto.GeoFlowItem> geoFlows(AppUserPrincipal actor) {
        return cache.getOrLoad(key(actor.getOrgId(), "geo-flows"),
                new TypeReference<>() {}, redisProperties.getCacheTtlSeconds(), () -> computeGeoFlows(actor));
    }

    @Transactional(readOnly = true)
    public List<com.sentinelai.dashboard.dto.GeoFlowItem> computeGeoFlows(AppUserPrincipal actor) {
        Map<String, Long> total = new LinkedHashMap<>();
        Map<String, String> topType = new LinkedHashMap<>();
        Map<String, Long> topTypeCount = new LinkedHashMap<>();
        for (Object[] row : securityEventRepository.countByGeoCountryAndType(actor.getOrgId())) {
            String country = (String) row[0];
            String type = String.valueOf(row[1]);
            long count = (Long) row[2];
            total.merge(country, count, Long::sum);
            if (count > topTypeCount.getOrDefault(country, 0L)) {
                topTypeCount.put(country, count);
                topType.put(country, type);
            }
        }
        return total.entrySet().stream()
                .map(e -> new com.sentinelai.dashboard.dto.GeoFlowItem(e.getKey(), e.getValue(), topType.get(e.getKey())))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardSummary computeSummary(AppUserPrincipal actor) {
        Long org = actor.getOrgId();
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        Map<String, Long> eventsBySeverity = new LinkedHashMap<>();
        for (Severity s : Severity.values()) {
            eventsBySeverity.put(s.name(), securityEventRepository.countByOrg_IdAndSeverity(org, s));
        }
        Map<String, Long> eventsByType = new LinkedHashMap<>();
        for (EventType t : EventType.values()) {
            eventsByType.put(t.name(), securityEventRepository.countByOrg_IdAndEventType(org, t));
        }
        Map<String, Long> incidentsByStatus = new LinkedHashMap<>();
        for (IncidentStatus st : IncidentStatus.values()) {
            incidentsByStatus.put(st.name(), incidentRepository.countByOrg_IdAndStatus(org, st));
        }
        Map<String, Long> incidentsBySeverity = new LinkedHashMap<>();
        for (Severity s : Severity.values()) {
            incidentsBySeverity.put(s.name(), incidentRepository.countByOrg_IdAndSeverity(org, s));
        }
        long eventsLast24h = securityEventRepository.countByOrg_IdAndEventTimestampAfter(org, since);
        return new DashboardSummary(eventsLast24h, eventsBySeverity, eventsByType,
                incidentsByStatus, incidentsBySeverity);
    }

    @Transactional(readOnly = true)
    public AlertReductionResponse computeAlertReduction(AppUserPrincipal actor) {
        Long org = actor.getOrgId();
        long events = securityEventRepository.countByOrg_Id(org);
        long alerts = alertRepository.countByOrg_Id(org);
        long incidents = incidentRepository.countByOrg_Id(org);
        double reductionPct = events == 0 ? 0.0
                : Math.round((1.0 - (double) incidents / events) * 10000.0) / 100.0;
        return new AlertReductionResponse(events, alerts, incidents, reductionPct);
    }

    @Transactional(readOnly = true)
    public List<MitreCoverageItem> computeMitreCoverage(AppUserPrincipal actor) {
        Long org = actor.getOrgId();

        Map<String, Long> alertCounts = new LinkedHashMap<>();
        for (Object[] row : alertRepository.countByMitreTechnique(org)) {
            if (row[0] != null) {
                alertCounts.put((String) row[0], (Long) row[1]);
            }
        }
        Map<String, Long> ruleCounts = new LinkedHashMap<>();
        for (DetectionRule rule : ruleRepository.findByOrg_IdAndEnabledTrue(org)) {
            if (rule.getMitreTechnique() != null) {
                ruleCounts.merge(rule.getMitreTechnique(), 1L, Long::sum);
            }
        }

        TreeSet<String> techniques = new TreeSet<>();
        techniques.addAll(alertCounts.keySet());
        techniques.addAll(ruleCounts.keySet());
        return techniques.stream()
                .map(t -> new MitreCoverageItem(t, ruleCounts.getOrDefault(t, 0L), alertCounts.getOrDefault(t, 0L)))
                .toList();
    }
}
