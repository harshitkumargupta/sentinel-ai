package com.sentinelai.event.service;

import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.dto.CreateEventRequest;
import com.sentinelai.event.dto.EventResponse;
import com.sentinelai.event.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EventService {

    private final SecurityEventRepository securityEventRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public EventResponse create(CreateEventRequest req, AppUserPrincipal actor) {
        SecurityEvent event = securityEventRepository.save(SecurityEvent.builder()
                .org(organizationRepository.getReferenceById(actor.getOrgId()))
                .eventType(req.eventType())
                .severity(req.severity())
                .sourceIp(req.sourceIp())
                .username(req.username())
                .userAgent(req.userAgent())
                .resource(req.resource())
                .assetCriticality(req.assetCriticality())
                .rawPayload(req.rawPayload())
                .geoCountry(req.geoCountry())
                .geoCity(req.geoCity())
                .honeytoken(Boolean.TRUE.equals(req.honeytoken()))
                .entityKey(req.entityKey())
                .correlationKey(req.correlationKey())
                .eventTimestamp(req.eventTimestamp() != null ? req.eventTimestamp() : Instant.now())
                .build());
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> list(AppUserPrincipal actor, String ip, String username,
                                    EventType type, Severity severity, Instant from, Instant to,
                                    Pageable pageable) {
        return securityEventRepository
                .findAll(EventSpecifications.build(actor.getOrgId(), ip, username, type, severity, from, to),
                        pageable)
                .map(EventResponse::from);
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long id, AppUserPrincipal actor) {
        SecurityEvent event = securityEventRepository.findById(id)
                .filter(e -> e.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Event not found: " + id));
        return EventResponse.from(event);
    }
}
