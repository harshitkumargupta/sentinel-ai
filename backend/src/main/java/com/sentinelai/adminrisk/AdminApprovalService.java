package com.sentinelai.adminrisk;

import com.sentinelai.adminrisk.domain.PendingActionStatus;
import com.sentinelai.adminrisk.domain.PendingAdminAction;
import com.sentinelai.adminrisk.domain.PendingAdminActionRepository;
import com.sentinelai.audit.service.AuditService;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.auth.service.UserService;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Approve/reject HIGH-risk pending admin actions. Enforces no self-approval and expiry. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminApprovalService {

    private final PendingAdminActionRepository pendingRepository;
    private final UserService userService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PendingAdminAction> listPending(Long orgId) {
        return pendingRepository.findByOrgIdAndStatusOrderByIdDesc(orgId, PendingActionStatus.PENDING);
    }

    // Not @Transactional: the expiry-marking below must persist even though the method then throws.
    // Each repository call commits on its own (adequate for this single-instance deployment).
    public PendingAdminAction approve(Long id, AppUserPrincipal approver) {
        PendingAdminAction p = load(id, approver.getOrgId());
        requirePending(p);
        if (clock.instant().isAfter(p.getExpiresAt())) {
            p.setStatus(PendingActionStatus.EXPIRED);
            p.setResolvedAt(clock.instant());
            pendingRepository.save(p);
            throw new BadRequestException("Pending action has expired");
        }
        if (approver.getUserId().equals(p.getRequestedBy())) {
            throw new BadRequestException("You cannot approve your own action");
        }
        p.setStatus(PendingActionStatus.APPROVED);
        p.setApprovedBy(approver.getUserId());
        p.setResolvedAt(clock.instant());
        execute(p, approver);
        p.setStatus(PendingActionStatus.EXECUTED);
        pendingRepository.save(p);
        auditService.record(approver.getOrgId(), approver.getUserId(), "ADMIN_ACTION_APPROVED",
                p.getEntityType(), p.getEntityId(), "{\"pendingId\":" + id + "}", null);
        return p;
    }

    @Transactional
    public PendingAdminAction reject(Long id, AppUserPrincipal approver) {
        PendingAdminAction p = load(id, approver.getOrgId());
        requirePending(p);
        p.setStatus(PendingActionStatus.REJECTED);
        p.setApprovedBy(approver.getUserId());
        p.setResolvedAt(clock.instant());
        pendingRepository.save(p);
        auditService.record(approver.getOrgId(), approver.getUserId(), "ADMIN_ACTION_REJECTED",
                p.getEntityType(), p.getEntityId(), "{\"pendingId\":" + id + "}", null);
        return p;
    }

    private void execute(PendingAdminAction p, AppUserPrincipal approver) {
        switch (p.getAction()) {
            case "USER_DISABLE" -> userService.disable(p.getEntityId(), approver);
            default -> log.warn("No executor for pending action {}", p.getAction());
        }
    }

    private void requirePending(PendingAdminAction p) {
        if (p.getStatus() != PendingActionStatus.PENDING) {
            throw new BadRequestException("Action is not pending (" + p.getStatus() + ")");
        }
    }

    private PendingAdminAction load(Long id, Long orgId) {
        PendingAdminAction p = pendingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pending action not found: " + id));
        if (!p.getOrgId().equals(orgId)) {
            throw new NotFoundException("Pending action not found: " + id);
        }
        return p;
    }
}
