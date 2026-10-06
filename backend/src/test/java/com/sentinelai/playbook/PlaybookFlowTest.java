package com.sentinelai.playbook;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.InvalidStateTransitionException;
import com.sentinelai.playbook.domain.PlaybookAction;
import com.sentinelai.playbook.domain.PlaybookActionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** State machine, safety rules, RBAC, idempotency, rollback and adapter-failure handling. */
@SpringBootTest(properties = {"sentinel.ai.enabled=false", "sentinel.kafka.enabled=false"})
@ActiveProfiles("test")
class PlaybookFlowTest extends PlaybookTestSupport {

    private static final String PUBLIC_IP = "203.0.113.50";

    @Test
    void dryRunDoesNotChangeState() {
        PlaybookAction a = propose("block_ip", PUBLIC_IP, Severity.LOW, admin);
        playbookService.dryRun(a.getId(), principal(analyst));
        PlaybookAction after = reload(a.getId());
        assertThat(after.getStatus()).isEqualTo(PlaybookActionStatus.PROPOSED);
        assertThat(after.getDryRunResult()).contains("affectedUsers");
    }

    @Test
    void approveExecuteRollbackHappyPath() {
        PlaybookAction a = propose("block_ip", PUBLIC_IP, Severity.LOW, admin);
        playbookService.approve(a.getId(), principal(analyst), "198.51.100.1", false);
        playbookService.execute(a.getId(), principal(analyst));
        assertThat(firewall.isBlocked(PUBLIC_IP)).isTrue();
        assertThat(reload(a.getId()).getStatus()).isEqualTo(PlaybookActionStatus.EXECUTED);

        playbookService.rollback(a.getId(), principal(analyst));
        assertThat(firewall.isBlocked(PUBLIC_IP)).isFalse();
        assertThat(reload(a.getId()).getStatus()).isEqualTo(PlaybookActionStatus.ROLLED_BACK);
    }

    @Test
    void selfApprovalRejectedForHighCritical() {
        PlaybookAction a = propose("block_ip", PUBLIC_IP, Severity.CRITICAL, admin);
        assertThatThrownBy(() -> playbookService.approve(a.getId(), principal(admin), "198.51.100.1", true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void highRiskRequiresAdminApprover() {
        PlaybookAction a = propose("block_ip", PUBLIC_IP, Severity.HIGH, analyst);
        assertThatThrownBy(() -> playbookService.approve(a.getId(), principal(analyst), "198.51.100.1", false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void analystApprovesLowMedium() {
        PlaybookAction a = propose("block_ip", PUBLIC_IP, Severity.MEDIUM, admin);
        playbookService.approve(a.getId(), principal(analyst), "198.51.100.1", false);
        assertThat(reload(a.getId()).getStatus()).isEqualTo(PlaybookActionStatus.APPROVED);
    }

    @Test
    void adminApprovesHighRiskProposedByAnother() {
        PlaybookAction a = propose("block_ip", PUBLIC_IP, Severity.HIGH, analyst);
        playbookService.approve(a.getId(), principal(admin), "198.51.100.1", true);
        assertThat(reload(a.getId()).getStatus()).isEqualTo(PlaybookActionStatus.APPROVED);
    }

    @Test
    void protectedIpIsRefused() {
        PlaybookAction a = propose("block_ip", "10.0.0.5", Severity.LOW, admin);
        playbookService.approve(a.getId(), principal(analyst), "198.51.100.1", false);
        assertThatThrownBy(() -> playbookService.execute(a.getId(), principal(analyst)))
                .isInstanceOf(BadRequestException.class);
        assertThat(firewall.isBlocked("10.0.0.5")).isFalse();
    }

    @Test
    void protectedAdminUserIsRefused() {
        PlaybookAction a = propose("disable_user", admin.getUsername(), Severity.LOW, analyst);
        assertThat(playbookService.dryRun(a.getId(), principal(analyst)).dryRun().get("allowed").asBoolean()).isFalse();
        playbookService.approve(a.getId(), principal(analyst), "198.51.100.1", false);
        assertThatThrownBy(() -> playbookService.execute(a.getId(), principal(analyst)))
                .isInstanceOf(BadRequestException.class);
        assertThat(identity.isDisabled(admin.getUsername())).isFalse();
    }

    @Test
    void expiredApprovalIsRejected() {
        PlaybookAction a = propose("block_ip", PUBLIC_IP, Severity.LOW, admin);
        a.setExpiresAt(Instant.now(clock).minus(1, ChronoUnit.MINUTES));
        playbookActionRepository.save(a);
        assertThatThrownBy(() -> playbookService.approve(a.getId(), principal(analyst), "198.51.100.1", false))
                .isInstanceOf(InvalidStateTransitionException.class);
        // The scheduled expiry sweep flips it to EXPIRED.
        playbookService.expireStale();
        assertThat(reload(a.getId()).getStatus()).isEqualTo(PlaybookActionStatus.EXPIRED);
    }

    @Test
    void executeIsIdempotent() {
        PlaybookAction a = propose("block_ip", PUBLIC_IP, Severity.LOW, admin);
        playbookService.approve(a.getId(), principal(analyst), "198.51.100.1", false);
        playbookService.execute(a.getId(), principal(analyst));
        playbookService.execute(a.getId(), principal(analyst)); // no-op
        assertThat(reload(a.getId()).getStatus()).isEqualTo(PlaybookActionStatus.EXECUTED);
        assertThat(firewall.isBlocked(PUBLIC_IP)).isTrue();
    }

    @Test
    void illegalTransitionThrows() {
        PlaybookAction a = propose("block_ip", PUBLIC_IP, Severity.LOW, admin);
        assertThatThrownBy(() -> playbookService.execute(a.getId(), principal(analyst)))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void adapterFailureAfterRetriesMovesToFailed() {
        PlaybookAction a = propose("block_ip", PUBLIC_IP, Severity.LOW, admin);
        playbookService.approve(a.getId(), principal(analyst), "198.51.100.1", false);
        firewall.failNext(99); // exceeds retries
        playbookService.execute(a.getId(), principal(analyst));
        PlaybookAction after = reload(a.getId());
        assertThat(after.getStatus()).isEqualTo(PlaybookActionStatus.FAILED);
        assertThat(after.getFailureReason()).isNotBlank();
        firewall.failNext(0);
    }

    @Test
    void adapterFailureWithinRetriesSucceeds() {
        PlaybookAction a = propose("block_ip", PUBLIC_IP, Severity.LOW, admin);
        playbookService.approve(a.getId(), principal(analyst), "198.51.100.1", false);
        firewall.failNext(1); // one transient failure, then succeed (maxRetries=2)
        playbookService.execute(a.getId(), principal(analyst));
        assertThat(reload(a.getId()).getStatus()).isEqualTo(PlaybookActionStatus.EXECUTED);
    }
}
