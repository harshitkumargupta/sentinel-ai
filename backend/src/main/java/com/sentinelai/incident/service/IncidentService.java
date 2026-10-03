package com.sentinelai.incident.service;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.event.dto.EventResponse;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.dto.IncidentDetailResponse;
import com.sentinelai.incident.dto.IncidentResponse;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IncidentService {

    /** Allowed status transitions. */
    private static final Map<IncidentStatus, Set<IncidentStatus>> TRANSITIONS =
            new EnumMap<>(IncidentStatus.class);

    static {
        TRANSITIONS.put(IncidentStatus.OPEN,
                Set.of(IncidentStatus.INVESTIGATING, IncidentStatus.FALSE_POSITIVE));
        TRANSITIONS.put(IncidentStatus.INVESTIGATING,
                Set.of(IncidentStatus.CONTAINED, IncidentStatus.RESOLVED,
                        IncidentStatus.FALSE_POSITIVE, IncidentStatus.OPEN));
        TRANSITIONS.put(IncidentStatus.CONTAINED,
                Set.of(IncidentStatus.RESOLVED, IncidentStatus.INVESTIGATING));
        TRANSITIONS.put(IncidentStatus.RESOLVED, Set.of(IncidentStatus.INVESTIGATING));
        TRANSITIONS.put(IncidentStatus.FALSE_POSITIVE, Set.of());
    }

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<IncidentResponse> list(AppUserPrincipal actor, IncidentStatus status,
                                       Severity severity, Pageable pageable) {
        Specification<Incident> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("org").get("id"), actor.getOrgId()));
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (severity != null) {
                p.add(cb.equal(root.get("severity"), severity));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return incidentRepository.findAll(spec, pageable).map(IncidentResponse::from);
    }

    @Transactional(readOnly = true)
    public IncidentDetailResponse getDetail(Long id, AppUserPrincipal actor) {
        Incident incident = load(id, actor);
        List<EventResponse> events = incidentEventRepository.findById_IncidentId(id).stream()
                .map(link -> EventResponse.from(link.getEvent()))
                .toList();
        return new IncidentDetailResponse(IncidentResponse.from(incident), events);
    }

    @Transactional
    public IncidentResponse updateStatus(Long id, IncidentStatus newStatus, AppUserPrincipal actor) {
        Incident incident = load(id, actor);
        IncidentStatus current = incident.getStatus();
        if (current == newStatus) {
            return IncidentResponse.from(incident);
        }
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(newStatus)) {
            throw new BadRequestException("Illegal status transition: " + current + " -> " + newStatus);
        }
        incident.setStatus(newStatus);
        if (newStatus == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(Instant.now());
        }
        incidentRepository.save(incident);
        auditService.record(actor.getOrgId(), actor.getUserId(), "INCIDENT_STATUS_CHANGE",
                "incident", id, "{\"from\":\"" + current + "\",\"to\":\"" + newStatus + "\"}", null);
        return IncidentResponse.from(incident);
    }

    @Transactional
    public IncidentResponse updateFeedback(Long id, IncidentFeedback feedback, AppUserPrincipal actor) {
        Incident incident = load(id, actor);
        incident.setFeedback(feedback);
        incidentRepository.save(incident);
        auditService.record(actor.getOrgId(), actor.getUserId(), "INCIDENT_FEEDBACK_CHANGE",
                "incident", id, "{\"feedback\":\"" + feedback + "\"}", null);
        return IncidentResponse.from(incident);
    }

    @Transactional
    public IncidentResponse assign(Long id, Long assigneeId, AppUserPrincipal actor) {
        Incident incident = load(id, actor);
        if (assigneeId == null) {
            incident.setAssignedTo(null);
        } else {
            User assignee = userRepository.findById(assigneeId)
                    .filter(u -> u.getOrg().getId().equals(actor.getOrgId()))
                    .orElseThrow(() -> new NotFoundException("Assignee not found: " + assigneeId));
            incident.setAssignedTo(assignee);
        }
        incidentRepository.save(incident);
        auditService.record(actor.getOrgId(), actor.getUserId(), "INCIDENT_ASSIGN", "incident", id,
                "{\"assigneeId\":" + assigneeId + "}", null);
        return IncidentResponse.from(incident);
    }

    private Incident load(Long id, AppUserPrincipal actor) {
        return incidentRepository.findById(id)
                .filter(i -> i.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Incident not found: " + id));
    }
}
