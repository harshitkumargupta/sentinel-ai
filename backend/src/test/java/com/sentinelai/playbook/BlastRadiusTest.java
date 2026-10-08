package com.sentinelai.playbook;

import com.sentinelai.asset.Asset;
import com.sentinelai.asset.AssetCriticality;
import com.sentinelai.asset.AssetEnvironment;
import com.sentinelai.asset.AssetRepository;
import com.sentinelai.asset.AssetType;
import com.sentinelai.audit.domain.AuditLog;
import com.sentinelai.audit.repository.AuditLogRepository;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.ConflictException;
import com.sentinelai.playbook.domain.PlaybookAction;
import com.sentinelai.playbook.domain.PlaybookActionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Blast-radius preview in the dry-run and audit; HIGH impact needs explicit confirmation to execute. */
@SpringBootTest(properties = {"sentinel.ai.enabled=false", "sentinel.kafka.enabled=false"})
@ActiveProfiles("test")
class BlastRadiusTest extends PlaybookTestSupport {

    @Autowired private BlastRadiusService blastRadius;
    @Autowired private AssetRepository assets;
    @Autowired private AuditLogRepository auditLogs;

    @AfterEach
    void cleanup() {
        assets.deleteAll();
    }

    @Test
    void lowImpactPreviewAndNormalExecute() {
        var p = blastRadius.preview(ORG_ID, "block_ip", "203.0.113.9");
        assertThat(p.users()).containsExactly("mallory");
        assertThat(p.impact()).isEqualTo(BlastRadiusService.Impact.LOW);
        assertThat(p.requiresConfirmation()).isFalse();

        PlaybookAction a = propose("block_ip", "203.0.113.9", Severity.LOW, admin);
        playbookService.dryRun(a.getId(), principal(analyst));
        assertThat(reload(a.getId()).getDryRunResult()).contains("\"preview\"").containsPattern("\"impact\":\\s?\"LOW\"");
        assertThat(auditLogs.findByEntityTypeAndEntityId("playbook_action", a.getId())).extracting(AuditLog::getDetails)
                .anyMatch(d -> d.contains("\"preview\""));
        playbookService.approve(a.getId(), principal(analyst), "198.51.100.1", false);
        playbookService.execute(a.getId(), principal(analyst));
        assertThat(reload(a.getId()).getStatus()).isEqualTo(PlaybookActionStatus.EXECUTED);
    }

    @Test
    void criticalAssetMakesItHighImpactAndNeedsConfirmation() {
        assets.save(Asset.builder().org(organizationRepository.findById(ORG_ID).orElseThrow()).hostname("edge-gw").ip("203.0.113.9")
                .type(AssetType.NETWORK).environment(AssetEnvironment.PRODUCTION).criticality(AssetCriticality.CRITICAL).build());
        var p = blastRadius.preview(ORG_ID, "block_ip", "203.0.113.9");
        assertThat(p.targetIsCriticalAsset()).isTrue();
        assertThat(p.assets()).extracting(BlastRadiusService.AssetImpact::criticality).containsExactly("CRITICAL");
        assertThat(p.impact()).isEqualTo(BlastRadiusService.Impact.HIGH);

        PlaybookAction a = propose("block_ip", "203.0.113.9", Severity.LOW, admin);
        playbookService.approve(a.getId(), principal(analyst), "198.51.100.1", false);
        assertThatThrownBy(() -> playbookService.execute(a.getId(), principal(analyst), false))
                .isInstanceOf(ConflictException.class).hasMessageContaining("HIGH/CRITICAL asset");
        assertThat(reload(a.getId()).getStatus()).isEqualTo(PlaybookActionStatus.APPROVED);
        playbookService.execute(a.getId(), principal(analyst), true);
        assertThat(reload(a.getId()).getStatus()).isEqualTo(PlaybookActionStatus.EXECUTED);
    }

    @Test
    void adminTargetIsFlagged() {
        var p = blastRadius.preview(ORG_ID, "disable_user", admin.getUsername());
        assertThat(p.targetIsAdmin()).isTrue();
        assertThat(p.impact()).isEqualTo(BlastRadiusService.Impact.HIGH);
        assertThat(p.reasons()).contains("affects an administrator account");
    }
}
