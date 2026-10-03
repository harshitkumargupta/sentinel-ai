package com.sentinelai.adminrisk;

import com.sentinelai.adminrisk.domain.PendingActionStatus;
import com.sentinelai.adminrisk.domain.PendingAdminAction;
import com.sentinelai.adminrisk.domain.PendingAdminActionRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminApprovalServiceTest extends IntegrationTestSupport {

    @Autowired private AdminApprovalService approvalService;
    @Autowired private PendingAdminActionRepository pendingRepository;

    private Long pending(Long requesterId, Long targetUserId, Instant expiresAt) {
        return pendingRepository.save(PendingAdminAction.builder()
                .orgId(ORG_ID).requestedBy(requesterId).action("USER_DISABLE")
                .entityType("user").entityId(targetUserId).payload("{\"enabled\":false}")
                .riskScore(55).status(PendingActionStatus.PENDING)
                .expiresAt(expiresAt).build()).getId();
    }

    @Test
    void selfApprovalIsRejected() {
        AppUserPrincipal requester = new AppUserPrincipal(createUser("req1", Role.ADMIN));
        User target = createUser("victim1", Role.VIEWER);
        Long id = pending(requester.getUserId(), target.getId(), Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> approvalService.approve(id, requester))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void differentAdminApprovesAndExecutes() {
        AppUserPrincipal requester = new AppUserPrincipal(createUser("req2", Role.ADMIN));
        AppUserPrincipal approver = new AppUserPrincipal(createUser("appr2", Role.ADMIN));
        User target = createUser("victim2", Role.VIEWER);
        Long id = pending(requester.getUserId(), target.getId(), Instant.now().plusSeconds(600));

        approvalService.approve(id, approver);

        assertThat(pendingRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.EXECUTED);
        assertThat(userRepository.findById(target.getId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void expiredActionCannotBeApproved() {
        AppUserPrincipal requester = new AppUserPrincipal(createUser("req3", Role.ADMIN));
        AppUserPrincipal approver = new AppUserPrincipal(createUser("appr3", Role.ADMIN));
        User target = createUser("victim3", Role.VIEWER);
        Long id = pending(requester.getUserId(), target.getId(), Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> approvalService.approve(id, approver))
                .isInstanceOf(BadRequestException.class);
        assertThat(pendingRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.EXPIRED);
    }
}
