package com.sentinelai.incident.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.alert.dto.AlertResponse;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.cache.IncidentsChangedEvent;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.InvalidStateTransitionException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.event.dto.EventResponse;
import com.sentinelai.incident.correlation.TimelineService;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.domain.IncidentPriority;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.dto.EvidenceResponse;
import com.sentinelai.incident.dto.IncidentDetailResponse;
import com.sentinelai.incident.dto.IncidentResponse;
import com.sentinelai.incident.dto.RiskResponse;
import com.sentinelai.incident.dto.TimelineEntryResponse;
import com.sentinelai.incident.repository.IncidentAlertRepository;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.incident.repository.IncidentTimelineRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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

    /**
     * Case workflow (UI labels in brackets): OPEN [New] → INVESTIGATING [In Progress] → CONTAINED →
     * RESOLVED → CLOSED. In Progress may resolve directly; RESOLVED may be reopened to In Progress;
     * FALSE_POSITIVE from any open state, then CLOSED. CLOSED is terminal.
     */
    private static final Map<IncidentStatus, Set<IncidentStatus>> TRANSITIONS =
            new EnumMap<>(IncidentStatus.class);

    static {
        TRANSITIONS.put(IncidentStatus.OPEN, Set.of(IncidentStatus.INVESTIGATING, IncidentStatus.FALSE_POSITIVE));
        TRANSITIONS.put(IncidentStatus.INVESTIGATING, Set.of(
                IncidentStatus.CONTAINED, IncidentStatus.RESOLVED, IncidentStatus.FALSE_POSITIVE));
        TRANSITIONS.put(IncidentStatus.CONTAINED, Set.of(IncidentStatus.RESOLVED, IncidentStatus.FALSE_POSITIVE));
        TRANSITIONS.put(IncidentStatus.RESOLVED, Set.of(IncidentStatus.CLOSED, IncidentStatus.INVESTIGATING));
        TRANSITIONS.put(IncidentStatus.FALSE_POSITIVE, Set.of(IncidentStatus.CLOSED));
        TRANSITIONS.put(IncidentStatus.CLOSED, Set.of());
    }

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentAlertRepository incidentAlertRepository;
    private final IncidentTimelineRepository timelineRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final TimelineService timeline;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<IncidentResponse> list(AppUserPrincipal actor, IncidentStatus status,
                                       Severity severity, Pageable pageable) {
        return list(actor, status, severity, null, null, false, pageable);
    }

    /** {@code unassigned=true} selects cases with no assignee (overrides {@code assigneeId}). */
    @Transactional(readOnly = true)
    public Page<IncidentResponse> list(AppUserPrincipal actor, IncidentStatus status, Severity severity,
                                       IncidentPriority priority, Long assigneeId, boolean unassigned,
                                       Pageable pageable) {
        Specification<Incident> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("org").get("id"), actor.getOrgId()));
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (severity != null) {
                p.add(cb.equal(root.get("severity"), severity));
            }
            if (priority != null) {
                p.add(cb.equal(root.get("priority"), priority));
            }
            if (unassigned) {
                p.add(cb.isNull(root.get("assignedTo")));
            } else if (assigneeId != null) {
                p.add(cb.equal(root.get("assignedTo").get("id"), assigneeId));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return incidentRepository.findAll(spec, pageable).map(IncidentResponse::from);
    }

    @Transactional(readOnly = true)
    public IncidentDetailResponse getDetail(Long id, AppUserPrincipal actor) {
        Incident incident = load(id, actor);
        List<EventResponse> events = incidentEventRepository.findById_IncidentId(id).stream()
                .map(link -> EventResponse.from(link.getEvent())).toList();
        return new IncidentDetailResponse(IncidentResponse.from(incident), events);
    }

    @Transactional(readOnly = true)
    public List<TimelineEntryResponse> getTimeline(Long id, AppUserPrincipal actor) {
        load(id, actor);
        return timelineRepository.findByIncidentIdOrderByIdAsc(id).stream()
                .map(TimelineEntryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RiskResponse getRisk(Long id, AppUserPrincipal actor) {
        Incident incident = load(id, actor);
        return RiskResponse.from(incident, objectMapper);
    }

    @Transactional(readOnly = true)
    public EvidenceResponse getEvidence(Long id, AppUserPrincipal actor) {
        load(id, actor);
        List<EventResponse> events = incidentEventRepository.findById_IncidentId(id).stream()
                .map(l -> EventResponse.from(l.getEvent())).toList();
        List<AlertResponse> alerts = incidentAlertRepository.findById_IncidentId(id).stream()
                .map(l -> AlertResponse.from(l.getAlert())).toList();
        return new EvidenceResponse(events, alerts);
    }

    @Transactional
    public IncidentResponse updateStatus(Long id, IncidentStatus newStatus, AppUserPrincipal actor) {
        Incident incident = load(id, actor);
        IncidentStatus current = incident.getStatus();
        if (current == newStatus) {
            return IncidentResponse.from(incident);
        }
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(newStatus)) {
            throw new InvalidStateTransitionException("Illegal status transition: " + current + " -> " + newStatus);
        }
        incident.setStatus(newStatus);
        if (newStatus == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(Instant.now());
        }
        if (newStatus == IncidentStatus.CLOSED) {
            incident.setClosedAt(Instant.now());
        }
        incidentRepository.save(incident);
        String detail = "{\"from\":\"" + current + "\",\"to\":\"" + newStatus + "\"}";
        timeline.record(id, TimelineService.STATUS_CHANGE, actor.getUsername(), detail);
        auditService.record(actor.getOrgId(), actor.getUserId(), "INCIDENT_STATUS_CHANGE", "incident", id, detail, null);
        events.publishEvent(new IncidentsChangedEvent(actor.getOrgId()));
        return IncidentResponse.from(incident);
    }

    @Transactional
    public IncidentResponse updateFeedback(Long id, IncidentFeedback feedback, AppUserPrincipal actor) {
        Incident incident = load(id, actor);
        incident.setFeedback(feedback);
        incidentRepository.save(incident);
        String detail = "{\"feedback\":\"" + feedback + "\"}";
        timeline.record(id, TimelineService.FEEDBACK, actor.getUsername(), detail);
        auditService.record(actor.getOrgId(), actor.getUserId(), "INCIDENT_FEEDBACK_CHANGE", "incident", id, detail, null);
        events.publishEvent(new IncidentsChangedEvent(actor.getOrgId()));
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
            if (!assignee.isEnabled() || (assignee.getRole() != Role.ANALYST && assignee.getRole() != Role.ADMIN)) {
                throw new BadRequestException("Cases can only be assigned to enabled analysts or admins");
            }
            incident.setAssignedTo(assignee);
        }
        incidentRepository.save(incident);
        String detail = "{\"assigneeId\":" + assigneeId + "}";
        timeline.record(id, TimelineService.ASSIGNMENT, actor.getUsername(), detail);
        auditService.record(actor.getOrgId(), actor.getUserId(), "INCIDENT_ASSIGN", "incident", id, detail, null);
        events.publishEvent(new IncidentsChangedEvent(actor.getOrgId()));
        return IncidentResponse.from(incident);
    }

    @Transactional
    public IncidentResponse setPriority(Long id, IncidentPriority priority, AppUserPrincipal actor) {
        Incident incident = load(id, actor);
        IncidentPriority current = incident.getPriority();
        if (current == priority) {
            return IncidentResponse.from(incident);
        }
        incident.setPriority(priority);
        incidentRepository.save(incident);
        String detail = "{\"from\":\"" + current + "\",\"to\":\"" + priority + "\"}";
        timeline.record(id, TimelineService.PRIORITY_CHANGE, actor.getUsername(), detail);
        auditService.record(actor.getOrgId(), actor.getUserId(), "INCIDENT_PRIORITY_CHANGE", "incident", id, detail, null);
        events.publishEvent(new IncidentsChangedEvent(actor.getOrgId()));
        return IncidentResponse.from(incident);
    }

    private Incident load(Long id, AppUserPrincipal actor) {
        return incidentRepository.findById(id)
                .filter(i -> i.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Incident not found: " + id));
    }
}
