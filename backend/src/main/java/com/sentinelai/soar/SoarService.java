package com.sentinelai.soar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.alert.domain.Alert;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.ConflictException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentAlert;
import com.sentinelai.incident.repository.IncidentAlertRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.notification.channel.IncidentNotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/** Playbook CRUD, manual runs, matching, run history, and auto-runs for new incidents. */
@Slf4j
@Service
public class SoarService {

    private final SoarPlaybookRepository playbooks;
    private final SoarRunRepository runs;
    private final SoarExecutor executor;
    private final IncidentRepository incidents;
    private final IncidentAlertRepository incidentAlerts;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final Executor background;

    public SoarService(SoarPlaybookRepository playbooks, SoarRunRepository runs, SoarExecutor executor,
                       IncidentRepository incidents, IncidentAlertRepository incidentAlerts, AuditService audit,
                       ObjectMapper objectMapper, @Qualifier("notificationExecutor") Executor background) {
        this.playbooks = playbooks;
        this.runs = runs;
        this.executor = executor;
        this.incidents = incidents;
        this.incidentAlerts = incidentAlerts;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.background = background;
    }

    public record PlaybookView(Long id, String name, String description, String triggerRuleType,
                               Severity triggerMinSeverity, List<PlaybookStep> steps, boolean enabled, boolean autoRun) {
    }

    public record RunView(Long id, Long playbookId, Long incidentId, SoarRun.Status status,
                          List<PlaybookStep.Result> steps, String triggeredBy, java.time.Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public List<PlaybookView> list(Long orgId) {
        return playbooks.findByOrgIdOrderByNameAsc(orgId).stream().map(this::view).toList();
    }

    @Transactional
    public PlaybookView save(Long orgId, Long userId, Long id, String name, String description, String ruleType,
                             Severity minSeverity, List<PlaybookStep> steps, boolean enabled, boolean autoRun) {
        if (steps == null || steps.isEmpty() || steps.size() > 20) {
            throw new BadRequestException("A playbook needs 1–20 steps");
        }
        for (PlaybookStep s : steps) {
            if (s.type() == null) {
                throw new BadRequestException("Every step needs a type");
            }
            if (s.type() == PlaybookStep.StepType.ADD_TO_WATCHLIST && (s.set() == null || s.set().isBlank())) {
                throw new BadRequestException("ADD_TO_WATCHLIST needs a reference-set name");
            }
            if (s.priority() != null && !s.priority().matches("^P[1-4]$")) {
                throw new BadRequestException("priority must be P1–P4");
            }
        }
        if ((ruleType == null || ruleType.isBlank()) && minSeverity == null) {
            throw new BadRequestException("Set a trigger: a detection rule type and/or a minimum severity");
        }
        SoarPlaybook pb = id == null ? new SoarPlaybook() : load(orgId, id);
        String n = name.trim();
        if ((id == null || !n.equals(pb.getName())) && playbooks.existsByOrgIdAndName(orgId, n)) {
            throw new ConflictException("A playbook with that name already exists");
        }
        pb.setOrgId(orgId);
        pb.setName(n);
        pb.setDescription(description == null || description.isBlank() ? null : description.trim());
        pb.setTriggerRuleType(ruleType == null || ruleType.isBlank() ? null : ruleType.trim());
        pb.setTriggerMinSeverity(minSeverity);
        try {
            pb.setSteps(objectMapper.writeValueAsString(steps));
        } catch (Exception e) {
            throw new BadRequestException("Invalid steps");
        }
        pb.setEnabled(enabled);
        pb.setAutoRun(autoRun);
        SoarPlaybook saved = playbooks.save(pb);
        audit.record(orgId, userId, id == null ? "SOAR_PLAYBOOK_CREATE" : "SOAR_PLAYBOOK_UPDATE", "soar_playbook", saved.getId(), "{}", null);
        return view(saved);
    }

    @Transactional
    public void delete(Long orgId, Long userId, Long id) {
        playbooks.delete(load(orgId, id));
        audit.record(orgId, userId, "SOAR_PLAYBOOK_DELETE", "soar_playbook", id, "{}", null);
    }

    @Transactional
    public RunView run(Long id, Long incidentId, AppUserPrincipal actor) {
        SoarPlaybook pb = load(actor.getOrgId(), id);
        if (!pb.isEnabled()) {
            throw new BadRequestException("Playbook is disabled");
        }
        return view(executor.run(pb, incidentId, actor.getUserId(), actor.getUsername()));
    }

    /** Enabled playbooks whose trigger matches this incident (for the incident page). */
    @Transactional(readOnly = true)
    public List<PlaybookView> matching(Long incidentId, AppUserPrincipal actor) {
        Incident i = incidents.findById(incidentId).filter(x -> x.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Incident not found: " + incidentId));
        Set<String> types = ruleTypes(incidentId);
        return playbooks.findByOrgIdOrderByNameAsc(actor.getOrgId()).stream()
                .filter(p -> p.isEnabled() && matches(p, i.getSeverity(), types)).map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<RunView> runs(Long orgId) {
        return runs.findByOrgIdOrderByIdDesc(orgId, PageRequest.of(0, 100)).stream().map(this::view).toList();
    }

    /** Auto-run playbooks flagged autoRun for newly created incidents (after commit, off-thread). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIncident(IncidentNotificationEvent e) {
        if (!e.created()) {
            return;
        }
        background.execute(() -> {
            for (SoarPlaybook pb : playbooks.findByOrgIdOrderByNameAsc(e.orgId())) {
                if (pb.isEnabled() && pb.isAutoRun() && matches(pb, e.severity(), e.ruleTypes())) {
                    try {
                        executor.run(pb, e.incidentId(), null, "auto");
                    } catch (RuntimeException ex) {
                        log.warn("Auto-run of playbook {} on incident {} failed: {}", pb.getName(), e.incidentId(), ex.toString());
                    }
                }
            }
        });
    }

    static boolean matches(SoarPlaybook p, Severity severity, Set<String> ruleTypes) {
        boolean sev = p.getTriggerMinSeverity() == null || (severity != null && severity.ordinal() >= p.getTriggerMinSeverity().ordinal());
        boolean rule = p.getTriggerRuleType() == null || ruleTypes.contains(p.getTriggerRuleType());
        return sev && rule;
    }

    private Set<String> ruleTypes(Long incidentId) {
        return incidentAlerts.findById_IncidentId(incidentId).stream().map(IncidentAlert::getAlert)
                .map(Alert::getRuleType).collect(Collectors.toSet());
    }

    private SoarPlaybook load(Long orgId, Long id) {
        return playbooks.findById(id).filter(p -> p.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Playbook not found: " + id));
    }

    private PlaybookView view(SoarPlaybook p) {
        return new PlaybookView(p.getId(), p.getName(), p.getDescription(), p.getTriggerRuleType(),
                p.getTriggerMinSeverity(), executor.steps(p.getSteps()), p.isEnabled(), p.isAutoRun());
    }

    private RunView view(SoarRun r) {
        List<PlaybookStep.Result> steps;
        try {
            steps = objectMapper.readValue(r.getStepResults(), new TypeReference<List<PlaybookStep.Result>>() {});
        } catch (Exception e) {
            steps = List.of();
        }
        return new RunView(r.getId(), r.getPlaybookId(), r.getIncidentId(), r.getStatus(), steps, r.getTriggeredBy(), r.getCreatedAt());
    }
}
