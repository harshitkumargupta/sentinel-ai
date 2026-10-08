package com.sentinelai.playbook;

import com.sentinelai.audit.domain.AuditLog;
import com.sentinelai.audit.repository.AuditLogRepository;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.playbook.adapter.MockEndpoint;
import com.sentinelai.playbook.domain.PlaybookActionStatus;
import com.sentinelai.playbook.web.PlaybookActionResponse;
import com.sentinelai.playbook.web.ProposeActionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** One-click proposal from an incident + the full Isolate Host lifecycle on the mock EDR. */
@SpringBootTest(properties = {"sentinel.ai.enabled=false", "sentinel.kafka.enabled=false"})
@ActiveProfiles("test")
class PlaybookProposalTest extends PlaybookTestSupport {

    private static final String HOST = "WS-TEST-01";

    @Autowired private PlaybookProposalService proposalService;
    @Autowired private PlaybookProperties playbookProperties;
    @Autowired private MockEndpoint endpoint;
    @Autowired private AuditLogRepository auditLogRepository;

    @AfterEach
    void restoreApproverRule() {
        playbookProperties.setRequireDistinctApprover(true);
    }

    @Test
    void proposesOnlyEvidenceTargetsAndIsIdempotent() {
        PlaybookActionResponse a = proposalService.propose(incident.getId(),
                new ProposeActionRequest("block_ip", "203.0.113.9", null), principal(analyst));
        assertThat(a.status()).isEqualTo("PROPOSED");
        assertThat(a.riskLevel()).isEqualTo(incident.getSeverity().name());
        assertThat(a.proposedBy()).isEqualTo(analyst.getUsername());

        PlaybookActionResponse again = proposalService.propose(incident.getId(),
                new ProposeActionRequest("block_ip", "203.0.113.9", "dup"), principal(analyst));
        assertThat(again.id()).isEqualTo(a.id());

        assertThatThrownBy(() -> proposalService.propose(incident.getId(),
                new ProposeActionRequest("block_ip", "8.8.8.8", null), principal(analyst)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("evidence");
        assertThatThrownBy(() -> proposalService.propose(incident.getId(),
                new ProposeActionRequest("format_disk", "mallory", null), principal(analyst)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> proposalService.propose(999_999L,
                new ProposeActionRequest("block_ip", "203.0.113.9", null), principal(analyst)))
                .isInstanceOf(NotFoundException.class);

        assertThat(auditLogRepository.findByEntityTypeAndEntityId("playbook_action", a.id()))
                .extracting(AuditLog::getAction).contains("PLAYBOOK_PROPOSE");
    }

    @Test
    void targetsListIpsUsersAndHosts() {
        Incident hostIncident = hostIncident("eve");
        var targets = proposalService.targets(hostIncident.getId(), principal(analyst));
        assertThat(targets.users()).containsExactly("eve");
        assertThat(targets.hosts()).containsExactly(HOST);
        assertThat(targets.ips()).containsExactly("198.51.100.77");
    }

    @Test
    void isolateHostFullLifecycleIsSimulatedAndAudited() {
        Incident hostIncident = hostIncident("eve");
        var proposed = proposalService.propose(hostIncident.getId(),
                new ProposeActionRequest("isolate_host", HOST, null), principal(analyst));
        Long id = proposed.id();

        playbookService.dryRun(id, principal(analyst));
        assertThat(reload(id).getDryRunResult()).contains("Isolate host " + HOST);
        approve(id);
        playbookService.execute(id, principal(analyst));
        assertThat(endpoint.isIsolated(HOST)).isTrue();
        assertThat(reload(id).getStatus()).isEqualTo(PlaybookActionStatus.EXECUTED);

        playbookService.rollback(id, principal(analyst));
        assertThat(endpoint.isIsolated(HOST)).isFalse();
        assertThat(reload(id).getStatus()).isEqualTo(PlaybookActionStatus.ROLLED_BACK);

        assertThat(auditLogRepository.findByEntityTypeAndEntityId("playbook_action", id))
                .extracting(AuditLog::getAction)
                .contains("PLAYBOOK_PROPOSE", "PLAYBOOK_DRY_RUN", "PLAYBOOK_APPROVE",
                        "PLAYBOOK_EXECUTE", "PLAYBOOK_ROLLBACK");
    }

    @Test
    void distinctApproverRuleIsConfigurable() {
        var a = proposalService.propose(incident.getId(),
                new ProposeActionRequest("block_ip", "203.0.113.9", null), principal(admin));
        var action = reload(a.id());
        action.setRiskLevel(Severity.HIGH);
        playbookActionRepository.save(action);

        assertThatThrownBy(() -> playbookService.approve(a.id(), principal(admin), "198.51.100.1", true))
                .isInstanceOf(AccessDeniedException.class);

        playbookProperties.setRequireDistinctApprover(false); // demo profile setting
        playbookService.approve(a.id(), principal(admin), "198.51.100.1", true);
        assertThat(reload(a.id()).getStatus()).isEqualTo(PlaybookActionStatus.APPROVED);
    }

    /** Approve as whoever the risk level requires (ADMIN for HIGH/CRITICAL, proposer was the analyst). */
    private void approve(Long id) {
        Severity risk = reload(id).getRiskLevel();
        boolean high = risk != null && risk.ordinal() >= Severity.HIGH.ordinal();
        playbookService.approve(id, principal(high ? admin : analyst), "198.51.100.1", true);
    }

    private Incident hostIncident(String user) {
        for (int i = 0; i < 4; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("username", user);
            p.put("sourceIp", "198.51.100.77");
            p.put("entityKey", "host:" + HOST);
            p.put("eventTimestamp", BASE.plusSeconds(5000 + i * 10L).toString());
            ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
        }
        return incidentRepository.findAll().stream()
                .filter(i -> ("user:" + user).equals(i.getCorrelationKey()))
                .findFirst().orElseThrow();
    }
}
