package com.sentinelai.event.service;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Records SentinelAI's own activity (failed logins, admin actions) as {@code security_events}
 * so the platform monitors itself. {@code source_ip} marks these as self-originated.
 */
@Service
@RequiredArgsConstructor
public class SecurityEventRecorder {

    private final SecurityEventRepository securityEventRepository;
    private final OrganizationRepository organizationRepository;

    public void record(Long orgId, EventType type, Severity severity, String username,
                       String sourceIp, String resource, String payloadJson) {
        securityEventRepository.save(SecurityEvent.builder()
                .org(organizationRepository.getReferenceById(orgId))
                .eventType(type)
                .severity(severity)
                .username(username)
                .sourceIp(sourceIp != null ? sourceIp : "self")
                .resource(resource)
                .rawPayload(payloadJson)
                .honeytoken(false)
                .eventTimestamp(Instant.now())
                .build());
    }
}
