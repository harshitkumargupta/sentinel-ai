package com.sentinelai.ai.security;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.correlation.TimelineService;
import com.sentinelai.incident.domain.Incident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * When prompt-injection is detected in an incident's event data, this raises its own
 * {@code PROMPT_INJECTION} security event, records a timeline entry on the incident, and audit-logs
 * the detection. The marker event is written directly (not through ingestion) so it does not
 * re-enter detection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InjectionFlagService {

    private final SecurityEventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final TimelineService timeline;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public void flag(Incident incident, List<String> hits) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        Long orgId = incident.getOrg().getId();
        String entityKey = incident.getCorrelationKey();

        eventRepository.save(SecurityEvent.builder()
                .org(organizationRepository.getReferenceById(orgId))
                .eventType(EventType.PROMPT_INJECTION)
                .severity(Severity.HIGH)
                .resource("ai/investigation")
                .entityKey(entityKey)
                .correlationKey(entityKey)
                .honeytoken(false)
                .eventTimestamp(clock.instant())
                .build());

        timeline.record(incident.getId(), "AI_INJECTION_FLAGGED", "system",
                "{\"hits\":" + hits.size() + "}");
        auditService.record(orgId, null, "PROMPT_INJECTION_DETECTED", "incident", incident.getId(),
                "{\"hits\":" + hits.size() + "}", null);
        log.warn("Prompt-injection detected in incident {} ({} pattern hits); flagged, not followed",
                incident.getId(), hits.size());
    }
}
