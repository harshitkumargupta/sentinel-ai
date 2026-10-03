package com.sentinelai.dashboard.service;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.dashboard.dto.DashboardSummary;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SecurityEventRepository securityEventRepository;
    private final IncidentRepository incidentRepository;

    @Transactional(readOnly = true)
    public DashboardSummary summary(AppUserPrincipal actor) {
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
}
