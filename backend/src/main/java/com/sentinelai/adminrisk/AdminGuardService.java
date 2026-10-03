package com.sentinelai.adminrisk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.adminrisk.GuardDecision.Decision;
import com.sentinelai.adminrisk.domain.PendingActionStatus;
import com.sentinelai.adminrisk.domain.PendingAdminAction;
import com.sentinelai.adminrisk.domain.PendingAdminActionRepository;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.audit.repository.AuditLogRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.RefreshTokenRepository;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.notification.domain.Notification;
import com.sentinelai.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Risk-adaptive guard for admin actions: LOW allow, MEDIUM step-up, HIGH hold for a different
 * admin's approval, CRITICAL block + revoke the actor's sessions + notify other admins. Every
 * decision is audited with before/after state and the risk breakdown.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminGuardService {

    private final AdminRiskService riskService;
    private final BaselineService baselineService;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PendingAdminActionRepository pendingRepository;
    private final AdminRiskProperties properties;
    private final AdminRiskExplainer explainer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public GuardDecision guard(AppUserPrincipal actor, String action, String entityType, Long entityId,
                               String beforeJson, String afterJson, String ip, String country,
                               String device, Long siteId, boolean stepUpConfirmed) {
        Instant now = clock.instant();
        Instant windowStart = now.minusSeconds(properties.getBurstWindowSeconds());
        long recent = auditLogRepository.countByActorIdAndCreatedAtAfter(actor.getUserId(), windowStart);
        long adminCount = Math.max(1, userRepository.findByRole(Role.ADMIN).size());
        double peerAvg = (double) auditLogRepository.countByCreatedAtAfter(windowStart) / adminCount;

        AdminActionContext ctx = new AdminActionContext(actor.getOrgId(), actor.getUserId(),
                actor.getUsername(), action, entityType, entityId, ip, country, device, siteId, now,
                beforeJson, afterJson, baselineService.load(actor.getUserId()), recent, peerAvg);

        AdminRiskResult result = riskService.assess(ctx);
        Decision decision = decide(result.band(), stepUpConfirmed);
        Long pendingId = null;

        if (decision == Decision.PENDING_APPROVAL) {
            pendingId = pendingRepository.save(PendingAdminAction.builder()
                    .orgId(actor.getOrgId())
                    .requestedBy(actor.getUserId())
                    .action(action).entityType(entityType).entityId(entityId)
                    .payload(afterJson)
                    .riskScore(result.score())
                    .riskBreakdown(toJson(result.breakdown()))
                    .status(PendingActionStatus.PENDING)
                    .expiresAt(now.plus(properties.getPendingExpiryMinutes(), ChronoUnit.MINUTES))
                    .build()).getId();
        } else if (decision == Decision.BLOCKED) {
            refreshTokenRepository.revokeAllForUser(actor.getUserId()); // session revoke
            notifyOtherAdmins(actor, action, result);
        }

        String explanation = explainer.explain(ctx, result, decision.name());
        auditService.record(actor.getOrgId(), actor.getUserId(),
                "ADMIN_GUARD_" + decision.name(), entityType, entityId,
                auditDetail(beforeJson, afterJson, result, decision), ip);

        return new GuardDecision(decision, result.band(), result.score(), result.breakdown(),
                pendingId, explanation);
    }

    private Decision decide(RiskBand band, boolean stepUpConfirmed) {
        return switch (band) {
            case LOW -> Decision.ALLOW;
            case MEDIUM -> stepUpConfirmed ? Decision.ALLOW : Decision.STEP_UP;
            case HIGH -> Decision.PENDING_APPROVAL;
            case CRITICAL -> Decision.BLOCKED;
        };
    }

    private void notifyOtherAdmins(AppUserPrincipal actor, String action, AdminRiskResult result) {
        String msg = "Blocked CRITICAL admin action '" + action + "' by " + actor.getUsername()
                + " (risk " + result.score() + "); their sessions were revoked.";
        for (User admin : userRepository.findByRole(Role.ADMIN)) {
            if (!admin.getId().equals(actor.getUserId())) {
                notificationRepository.save(Notification.builder()
                        .user(admin).message(msg).read(false).build());
            }
        }
    }

    private String auditDetail(String before, String after, AdminRiskResult r, Decision d) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("before", before);
            m.put("after", after);
            m.put("risk", Map.of("score", r.score(), "band", r.band().name(), "decision", d.name()));
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }
}
