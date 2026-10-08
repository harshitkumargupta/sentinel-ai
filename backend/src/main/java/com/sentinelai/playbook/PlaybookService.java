package com.sentinelai.playbook;

import com.sentinelai.adminrisk.AdminGuardService;
import com.sentinelai.adminrisk.GuardDecision;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.InvalidStateTransitionException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.incident.correlation.TimelineService;
import com.sentinelai.playbook.action.ActionContext;
import com.sentinelai.playbook.action.DryRunResult;
import com.sentinelai.playbook.action.ExecutionResult;
import com.sentinelai.playbook.action.ResponseAction;
import com.sentinelai.playbook.adapter.AdapterException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.playbook.domain.PlaybookAction;
import com.sentinelai.playbook.domain.PlaybookActionStatus;
import com.sentinelai.playbook.repository.PlaybookActionRepository;
import com.sentinelai.playbook.web.PlaybookActionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SOAR playbook engine: the human-approved response lifecycle and its safety rules.
 *
 * <p>State machine: {@code PROPOSED → APPROVED → EXECUTED → ROLLED_BACK}, with {@code REJECTED},
 * {@code FAILED} and {@code EXPIRED} branches. Illegal transitions throw
 * {@link InvalidStateTransitionException}. Safety: protected targets are never acted on, destructive
 * actions must be on the allow-list, HIGH/CRITICAL actions need an ADMIN approver who differs from
 * the proposer and passes the admin-risk guard, approvals expire, execute is idempotent, and every
 * transition is audited (with before/after) and recorded on the incident timeline.
 */
@Slf4j
@Service
public class PlaybookService {

    private static final Map<PlaybookActionStatus, Set<PlaybookActionStatus>> TRANSITIONS =
            new EnumMap<>(PlaybookActionStatus.class);

    static {
        TRANSITIONS.put(PlaybookActionStatus.PROPOSED, Set.of(
                PlaybookActionStatus.APPROVED, PlaybookActionStatus.REJECTED, PlaybookActionStatus.EXPIRED));
        TRANSITIONS.put(PlaybookActionStatus.APPROVED, Set.of(
                PlaybookActionStatus.EXECUTED, PlaybookActionStatus.FAILED, PlaybookActionStatus.EXPIRED));
        TRANSITIONS.put(PlaybookActionStatus.EXECUTED, Set.of(PlaybookActionStatus.ROLLED_BACK));
        TRANSITIONS.put(PlaybookActionStatus.FAILED, Set.of());
        TRANSITIONS.put(PlaybookActionStatus.REJECTED, Set.of());
        TRANSITIONS.put(PlaybookActionStatus.ROLLED_BACK, Set.of());
        TRANSITIONS.put(PlaybookActionStatus.EXPIRED, Set.of());
    }

    private final Map<String, ResponseAction> actions;
    private final PlaybookActionRepository repository;
    private final PlaybookProperties props;
    private final AuditService auditService;
    private final TimelineService timeline;
    private final AdminGuardService adminGuard;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PlaybookService(List<ResponseAction> actionBeans, PlaybookActionRepository repository,
                           PlaybookProperties props, AuditService auditService, TimelineService timeline,
                           AdminGuardService adminGuard, UserRepository userRepository,
                           ObjectMapper objectMapper, Clock clock) {
        this.actions = actionBeans.stream().collect(Collectors.toMap(ResponseAction::type, Function.identity()));
        this.repository = repository;
        this.props = props;
        this.auditService = auditService;
        this.timeline = timeline;
        this.adminGuard = adminGuard;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Recommendation verbs that map onto a differently-named action ({@code monitor} → watchlist). */
    public static String actionTypeFor(String recommendation) {
        return "monitor".equals(recommendation) ? "add_watchlist" : recommendation;
    }

    /** True when an action handler is registered for this type. */
    public boolean supports(String actionType) {
        return actions.containsKey(actionType);
    }

    @Transactional(readOnly = true)
    public List<PlaybookActionResponse> listForIncident(Long incidentId, AppUserPrincipal actor) {
        return repository.findByIncident_IdOrderByIdDesc(incidentId).stream()
                .filter(a -> a.getIncident().getOrg().getId().equals(actor.getOrgId()))
                .map(this::toResponse)
                .toList();
    }

    /** Dry-run: compute and store the preview + blast radius. Never changes state. */
    @Transactional
    public PlaybookActionResponse dryRun(Long actionId, AppUserPrincipal actor) {
        PlaybookAction action = load(actionId, actor);
        DryRunResult result = responseAction(action).dryRun(ctx(action));
        action.setDryRunResult(toJson(result));
        repository.save(action);
        auditService.record(actor.getOrgId(), actor.getUserId(), "PLAYBOOK_DRY_RUN", "playbook_action",
                actionId, toJson(result), null);
        return toResponse(action);
    }

    /** Approve: enforces RBAC, proposer≠approver (HIGH/CRITICAL), expiry and the admin-risk guard. */
    @Transactional
    public PlaybookActionResponse approve(Long actionId, AppUserPrincipal actor, String ip, boolean stepUp) {
        PlaybookAction action = load(actionId, actor);
        requireNotExpired(action);
        Severity risk = action.getRiskLevel() == null ? Severity.LOW : action.getRiskLevel();
        boolean highRisk = risk.ordinal() >= Severity.HIGH.ordinal();

        if (highRisk && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("HIGH/CRITICAL actions require an ADMIN approver");
        }
        if (highRisk && props.isRequireDistinctApprover() && action.getProposedBy() != null
                && action.getProposedBy().getId().equals(actor.getUserId())) {
            throw new AccessDeniedException("The approver must differ from the proposer for HIGH/CRITICAL actions");
        }

        // The admin-risk guard applies to the approver for destructive, high-risk actions.
        if (highRisk) {
            GuardDecision decision = adminGuard.guard(actor, "PLAYBOOK_APPROVE", "playbook_action", actionId,
                    "{\"status\":\"PROPOSED\"}", "{\"status\":\"APPROVED\"}", ip, null, null, null, stepUp);
            if (!decision.allowed()) {
                throw new AccessDeniedException(
                        "Admin-risk guard did not allow the approval: " + decision.explanation());
            }
        }

        transition(action, PlaybookActionStatus.APPROVED);
        action.setApprovedBy(userRef(actor));
        action.setApprovedAt(Instant.now(clock));
        repository.save(action);
        audit(actor, action, "PLAYBOOK_APPROVE", "{\"risk\":\"" + risk + "\"}");
        timeline.record(action.getIncident().getId(), "ACTION_APPROVED", actor.getUsername(),
                "{\"action\":\"" + action.getActionType() + "\",\"target\":\"" + action.getTargetRef() + "\"}");
        return toResponse(action);
    }

    @Transactional
    public PlaybookActionResponse reject(Long actionId, AppUserPrincipal actor) {
        PlaybookAction action = load(actionId, actor);
        transition(action, PlaybookActionStatus.REJECTED);
        repository.save(action);
        audit(actor, action, "PLAYBOOK_REJECT", "{}");
        timeline.record(action.getIncident().getId(), "ACTION_REJECTED", actor.getUsername(),
                "{\"action\":\"" + action.getActionType() + "\"}");
        return toResponse(action);
    }

    /** Execute an approved action: allow-list + protected-target guard, idempotent, retried adapter. */
    @Transactional
    public PlaybookActionResponse execute(Long actionId, AppUserPrincipal actor) {
        PlaybookAction action = load(actionId, actor);
        if (action.getStatus() == PlaybookActionStatus.EXECUTED) {
            return toResponse(action); // idempotent: a repeated execute is a no-op
        }
        requireNotExpired(action);
        if (action.getStatus() != PlaybookActionStatus.APPROVED) {
            throw new InvalidStateTransitionException(
                    "Can only execute an APPROVED action (was " + action.getStatus() + ")");
        }
        ResponseAction impl = responseAction(action);
        if (impl.destructive() && !props.isAllowed(action.getActionType())) {
            throw new BadRequestException("Action type " + action.getActionType() + " is not on the allow-list");
        }
        // Re-check protected targets at execute time — they can never be acted on.
        DryRunResult dry = impl.dryRun(ctx(action));
        if (!dry.allowed()) {
            throw new BadRequestException("Refused: " + dry.reason());
        }

        try {
            ExecutionResult result = withRetries(() -> impl.execute(ctx(action)));
            action.setBeforeState(result.beforeState());
            action.setAfterState(result.afterState());
            action.setExecutedAt(Instant.now(clock));
            transition(action, PlaybookActionStatus.EXECUTED);
            repository.save(action);
            audit(actor, action, "PLAYBOOK_EXECUTE",
                    "{\"before\":" + result.beforeState() + ",\"after\":" + result.afterState() + "}");
            timeline.record(action.getIncident().getId(), "ACTION_EXECUTED", actor.getUsername(),
                    "{\"action\":\"" + action.getActionType() + "\",\"target\":\"" + action.getTargetRef()
                            + "\",\"result\":\"" + escape(result.message()) + "\"}");
            return toResponse(action);
        } catch (Exception e) {
            action.setFailureReason(truncate(e.getMessage()));
            transition(action, PlaybookActionStatus.FAILED);
            repository.save(action);
            audit(actor, action, "PLAYBOOK_FAILED", "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            timeline.record(action.getIncident().getId(), "ACTION_FAILED", actor.getUsername(),
                    "{\"action\":\"" + action.getActionType() + "\"}");
            log.warn("Playbook action {} failed after retries: {}", actionId, e.toString());
            return toResponse(action);
        }
    }

    @Transactional
    public PlaybookActionResponse rollback(Long actionId, AppUserPrincipal actor) {
        PlaybookAction action = load(actionId, actor);
        if (action.getStatus() != PlaybookActionStatus.EXECUTED) {
            throw new InvalidStateTransitionException(
                    "Can only roll back an EXECUTED action (was " + action.getStatus() + ")");
        }
        responseAction(action).rollback(ctx(action));
        action.setRolledBackAt(Instant.now(clock));
        transition(action, PlaybookActionStatus.ROLLED_BACK);
        repository.save(action);
        audit(actor, action, "PLAYBOOK_ROLLBACK",
                "{\"restored\":" + (action.getBeforeState() == null ? "null" : action.getBeforeState()) + "}");
        timeline.record(action.getIncident().getId(), "ACTION_ROLLED_BACK", actor.getUsername(),
                "{\"action\":\"" + action.getActionType() + "\"}");
        return toResponse(action);
    }

    /** Expire stale proposals/approvals. */
    @Transactional
    public int expireStale() {
        Instant now = Instant.now(clock);
        List<PlaybookAction> stale = repository.findByStatusInAndExpiresAtBefore(
                List.of(PlaybookActionStatus.PROPOSED, PlaybookActionStatus.APPROVED), now);
        for (PlaybookAction a : stale) {
            transition(a, PlaybookActionStatus.EXPIRED);
            repository.save(a);
            auditService.record(a.getIncident().getOrg().getId(), null, "PLAYBOOK_EXPIRE",
                    "playbook_action", a.getId(), "{}", null);
        }
        return stale.size();
    }

    // --- internals -----------------------------------------------------------------------------

    private void transition(PlaybookAction action, PlaybookActionStatus to) {
        if (!TRANSITIONS.getOrDefault(action.getStatus(), Set.of()).contains(to)) {
            throw new InvalidStateTransitionException(
                    "Illegal transition: " + action.getStatus() + " -> " + to);
        }
        action.setStatus(to);
    }

    private void requireNotExpired(PlaybookAction action) {
        // Don't mutate state here — this runs inside a tx that is about to roll back on the throw.
        // The scheduled expiry job flips the status to EXPIRED in its own transaction.
        if (action.getExpiresAt() != null && action.getExpiresAt().isBefore(Instant.now(clock))) {
            throw new InvalidStateTransitionException("Action has expired");
        }
    }

    private <T> T withRetries(java.util.concurrent.Callable<T> op) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt <= props.getAdapterMaxRetries(); attempt++) {
            try {
                return op.call();
            } catch (AdapterException e) {
                last = e;
                log.debug("Adapter attempt {} failed: {}", attempt + 1, e.getMessage());
            }
        }
        throw last != null ? last : new AdapterException("adapter failed");
    }

    private ResponseAction responseAction(PlaybookAction action) {
        ResponseAction impl = actions.get(action.getActionType());
        if (impl == null) {
            throw new BadRequestException("No action handler for type " + action.getActionType());
        }
        return impl;
    }

    private ActionContext ctx(PlaybookAction action) {
        return new ActionContext(action.getIncident().getOrg().getId(), action.getTargetRef());
    }

    private PlaybookAction load(Long id, AppUserPrincipal actor) {
        return repository.findById(id)
                .filter(a -> a.getIncident().getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Action not found: " + id));
    }

    private com.sentinelai.auth.domain.User userRef(AppUserPrincipal actor) {
        return userRepository.getReferenceById(actor.getUserId());
    }

    public PlaybookActionResponse toResponse(PlaybookAction action) {
        ResponseAction impl = actions.get(action.getActionType());
        return PlaybookActionResponse.from(action, objectMapper, impl != null && impl.destructive());
    }

    private void audit(AppUserPrincipal actor, PlaybookAction action, String op, String details) {
        auditService.record(actor.getOrgId(), actor.getUserId(), op, "playbook_action", action.getId(),
                details, null);
    }

    private String toJson(DryRunResult r) {
        return "{\"allowed\":" + r.allowed()
                + ",\"reason\":" + quote(r.reason())
                + ",\"summary\":" + quote(r.summary())
                + ",\"blastRadius\":" + quote(r.blastRadius())
                + ",\"affectedUsers\":" + r.affectedUsers()
                + ",\"affectedAdmins\":" + r.affectedAdmins() + "}";
    }

    private static String quote(String s) {
        return s == null ? "null" : "\"" + escape(s) + "\"";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
