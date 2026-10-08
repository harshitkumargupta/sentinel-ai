package com.sentinelai.playbook;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.incident.correlation.TimelineService;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentEvent;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.playbook.domain.PlaybookAction;
import com.sentinelai.playbook.domain.PlaybookActionStatus;
import com.sentinelai.playbook.repository.PlaybookActionRepository;
import com.sentinelai.playbook.web.PlaybookActionResponse;
import com.sentinelai.playbook.web.ProposeActionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One-click proposal of a response action straight from an incident (no AI review needed). The
 * action type must be registered and allow-listed, and the target must appear in the incident's own
 * evidence, so an analyst can't aim an action at an arbitrary IP/user/host. Idempotent: proposing the
 * same live action twice returns the existing one. The proposal then follows the normal
 * dry-run → approve → execute → rollback lifecycle in {@link PlaybookService}.
 */
@Service
@RequiredArgsConstructor
public class PlaybookProposalService {

    private static final List<PlaybookActionStatus> LIVE =
            List.of(PlaybookActionStatus.PROPOSED, PlaybookActionStatus.APPROVED);

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final PlaybookActionRepository repository;
    private final PlaybookService playbookService;
    private final PlaybookProperties props;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final TimelineService timeline;
    private final Clock clock;

    /** Entities in an incident's evidence that actions may target. */
    public record ActionTargets(List<String> ips, List<String> users, List<String> hosts) {
    }

    @Transactional(readOnly = true)
    public ActionTargets targets(Long incidentId, AppUserPrincipal actor) {
        return targetsOf(load(incidentId, actor));
    }

    @Transactional
    public PlaybookActionResponse propose(Long incidentId, ProposeActionRequest req, AppUserPrincipal actor) {
        Incident incident = load(incidentId, actor);
        return propose(incident, req.actionType(), req.target(), req.reason(), actor.getUserId(), actor.getUsername());
    }

    /**
     * Propose for an incident on behalf of a user or (userId null) an automation such as a SOAR
     * playbook. Same validation, idempotency, audit and timeline either way; approval stays human.
     */
    @Transactional
    public PlaybookActionResponse propose(Incident incident, String actionType, String rawTarget, String reason,
                                         Long userId, String actorName) {
        String type = PlaybookService.actionTypeFor(actionType.trim());
        if (!playbookService.supports(type) || !props.isAllowed(type)) {
            throw new BadRequestException("Unsupported or non-allow-listed action type: " + type);
        }
        String target = rawTarget.trim();
        ActionTargets targets = targetsOf(incident);
        if (!targets.ips().contains(target) && !targets.users().contains(target) && !targets.hosts().contains(target)) {
            throw new BadRequestException("Target is not part of this incident's evidence: " + target);
        }

        var existing = repository.findByIncident_IdOrderByIdDesc(incident.getId()).stream()
                .filter(a -> LIVE.contains(a.getStatus()))
                .filter(a -> type.equals(a.getActionType()) && target.equals(a.getTargetRef()))
                .findFirst();
        if (existing.isPresent()) {
            return playbookService.toResponse(existing.get());
        }

        PlaybookAction action = repository.save(PlaybookAction.builder()
                .incident(incident)
                .proposedBy(userId == null ? null : userRepository.getReferenceById(userId))
                .actionType(type)
                .targetRef(target)
                .reason(reason == null || reason.isBlank()
                        ? "Proposed by " + actorName + " from the incident page" : reason.trim())
                .riskLevel(incident.getSeverity())
                .expiresAt(clock.instant().plus(props.getExpiryMinutes(), ChronoUnit.MINUTES))
                .status(PlaybookActionStatus.PROPOSED)
                .build());
        auditService.record(incident.getOrg().getId(), userId, "PLAYBOOK_PROPOSE", "playbook_action",
                action.getId(), "{\"action\":\"" + type + "\",\"target\":\"" + escape(target) + "\",\"by\":\""
                        + escape(actorName) + "\"}", null);
        timeline.record(incident.getId(), "ACTION_PROPOSED", actorName,
                "{\"action\":\"" + type + "\",\"target\":\"" + escape(target) + "\"}");
        return playbookService.toResponse(action);
    }

    /** Evidence targets of an incident (IPs, users, hosts) — for automations. */
    @Transactional(readOnly = true)
    public ActionTargets targetsOf(Long incidentId) {
        return targetsOf(incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found: " + incidentId)));
    }

    private ActionTargets targetsOf(Incident incident) {
        List<SecurityEvent> events = incidentEventRepository.findById_IncidentId(incident.getId()).stream()
                .map(IncidentEvent::getEvent).toList();
        Set<String> ips = new LinkedHashSet<>();
        Set<String> users = new LinkedHashSet<>();
        Set<String> hosts = new LinkedHashSet<>();
        for (SecurityEvent e : events) {
            if (e.getSourceIp() != null) {
                ips.add(e.getSourceIp());
            }
            if (e.getUsername() != null) {
                users.add(e.getUsername());
            }
            String key = e.getEntityKey();
            if (key != null && key.startsWith("host:")) {
                hosts.add(key.substring("host:".length()));
            }
        }
        return new ActionTargets(List.copyOf(ips), List.copyOf(users),
                hosts.stream().filter(Objects::nonNull).toList());
    }

    private Incident load(Long incidentId, AppUserPrincipal actor) {
        return incidentRepository.findById(incidentId)
                .filter(i -> i.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Incident not found: " + incidentId));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
