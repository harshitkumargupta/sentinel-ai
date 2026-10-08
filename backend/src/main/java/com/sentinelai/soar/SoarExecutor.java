package com.sentinelai.soar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.incident.correlation.TimelineService;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentPriority;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.notification.channel.ChannelSender;
import com.sentinelai.notification.channel.NotificationChannelRepository;
import com.sentinelai.notification.channel.NotificationDispatcher;
import com.sentinelai.playbook.PlaybookProposalService;
import com.sentinelai.reference.ReferenceSet;
import com.sentinelai.reference.ReferenceSetItem;
import com.sentinelai.reference.ReferenceSetItemRepository;
import com.sentinelai.reference.ReferenceSetRepository;
import com.sentinelai.reference.ReferenceSetType;
import com.sentinelai.soar.PlaybookStep.Result;
import com.sentinelai.soar.PlaybookStep.StepType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a playbook's steps in order against one incident. Response steps only <em>propose</em>
 * actions through the existing engine (dry-run → approve → execute → rollback stay human), so
 * nothing outside SentinelAI changes. Each step's failure is recorded and the run continues.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoarExecutor {

    private final SoarRunRepository runs;
    private final IncidentRepository incidents;
    private final PlaybookProposalService proposals;
    private final NotificationChannelRepository channels;
    private final NotificationDispatcher notifier;
    private final ReferenceSetRepository sets;
    private final ReferenceSetItemRepository items;
    private final TimelineService timeline;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    @Transactional
    public SoarRun run(SoarPlaybook pb, Long incidentId, Long userId, String triggeredBy) {
        Incident incident = incidents.findById(incidentId).filter(i -> i.getOrg().getId().equals(pb.getOrgId()))
                .orElseThrow(() -> new com.sentinelai.common.exception.NotFoundException("Incident not found: " + incidentId));
        String actor = "playbook:" + pb.getName();
        List<Result> results = new ArrayList<>();
        for (PlaybookStep step : steps(pb.getSteps())) {
            try {
                results.add(execute(step, incident, actor));
            } catch (RuntimeException e) {
                results.add(new Result(step.type(), false, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        long ok = results.stream().filter(Result::ok).count();
        SoarRun.Status status = ok == results.size() ? SoarRun.Status.SUCCEEDED : ok == 0 ? SoarRun.Status.FAILED : SoarRun.Status.PARTIAL;
        SoarRun saved = runs.save(SoarRun.builder().orgId(pb.getOrgId()).playbookId(pb.getId()).incidentId(incidentId)
                .status(status).stepResults(json(results)).triggeredBy(triggeredBy).build());
        timeline.record(incidentId, "PLAYBOOK_RUN", actor, "{\"runId\":" + saved.getId() + ",\"status\":\"" + status + "\"}");
        audit.record(pb.getOrgId(), userId, "SOAR_PLAYBOOK_RUN", "incident", incidentId,
                "{\"playbook\":\"" + pb.getName().replace("\"", "'") + "\",\"status\":\"" + status + "\",\"by\":\""
                        + triggeredBy.replace("\"", "'") + "\"}", null);
        return saved;
    }

    private Result execute(PlaybookStep step, Incident incident, String actor) {
        var targets = proposals.targetsOf(incident.getId());
        return switch (step.type()) {
            case CREATE_CASE -> {
                StringBuilder d = new StringBuilder();
                if (incident.getStatus() == IncidentStatus.OPEN) {
                    incident.setStatus(IncidentStatus.INVESTIGATING);
                    timeline.record(incident.getId(), TimelineService.STATUS_CHANGE, actor, "{\"from\":\"OPEN\",\"to\":\"INVESTIGATING\"}");
                    d.append("status → In Progress");
                }
                if (step.priority() != null) {
                    incident.setPriority(IncidentPriority.valueOf(step.priority()));
                    d.append(d.isEmpty() ? "" : "; ").append("priority ").append(step.priority());
                }
                incidents.save(incident);
                yield new Result(step.type(), true, d.isEmpty() ? "case already open" : d.toString());
            }
            case NOTIFY -> {
                var targetsCh = channels.findByOrgIdOrderByIdAsc(incident.getOrg().getId()).stream()
                        .filter(c -> c.isEnabled() && (step.channelId() == null || c.getId().equals(step.channelId()))).toList();
                if (targetsCh.isEmpty()) {
                    yield new Result(step.type(), false, "no enabled channel");
                }
                String subject = "[SentinelAI] Playbook " + actor.substring(9) + " ran on incident #" + incident.getId()
                        + ": " + incident.getTitle();
                List<String> outcomes = targetsCh.stream().map(c -> c.getName() + " " + notifier.deliver(c, null,
                        new ChannelSender.Message(incident.getOrg().getId(), incident.getId(), subject, subject), false).getStatus()).toList();
                yield new Result(step.type(), true, String.join(", ", outcomes));
            }
            case PROPOSE_BLOCK_IP -> propose(step, incident, "block_ip", targets.ips(), actor);
            case PROPOSE_DISABLE_USER -> propose(step, incident, "disable_user", targets.users(), actor);
            case ADD_TO_WATCHLIST -> {
                ReferenceSet set = sets.findByOrg_IdAndName(incident.getOrg().getId(), step.set())
                        .orElseThrow(() -> new IllegalArgumentException("reference set '" + step.set() + "' not found"));
                List<String> values = set.getElementType() == ReferenceSetType.USERNAME ? targets.users() : targets.ips();
                int added = 0;
                for (String v : values) {
                    if (!items.existsBySet_IdAndValue(set.getId(), v)) {
                        items.save(ReferenceSetItem.builder().set(set).value(v).note("added by " + actor).build());
                        added++;
                    }
                }
                yield new Result(step.type(), true, "added " + added + " value(s) to " + set.getName());
            }
        };
    }

    private Result propose(PlaybookStep step, Incident incident, String type, List<String> candidates, String actor) {
        if (candidates.isEmpty()) {
            return new Result(step.type(), false, "no target in the incident's evidence");
        }
        List<String> done = new ArrayList<>();
        for (String t : candidates.stream().limit(3).toList()) {
            var a = proposals.propose(incident, type, t, "Proposed by " + actor + " (awaiting human approval)", null, actor);
            done.add(t + " (#" + a.id() + " " + a.status() + ")");
        }
        return new Result(step.type(), true, "proposed " + type + ": " + String.join(", ", done));
    }

    public List<PlaybookStep> steps(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<PlaybookStep>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid playbook steps: " + e.getMessage());
        }
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }
}
