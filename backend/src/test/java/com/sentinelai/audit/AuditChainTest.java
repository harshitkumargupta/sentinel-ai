package com.sentinelai.audit;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the audit hash chain: an intact chain validates, and tampering with any row is
 * detected and pinpointed.
 */
class AuditChainTest extends IntegrationTestSupport {

    @Autowired
    private AuditService auditService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearAuditLog() {
        jdbcTemplate.execute("DELETE FROM audit_logs");
    }

    @Test
    void intactChainVerifies() {
        auditService.record(ORG_ID, null, "LOGIN", "user", 1L, "{\"a\":1}", "127.0.0.1");
        auditService.record(ORG_ID, null, "RULE_CREATE", "detection_rule", 2L, "{\"b\":2}", "127.0.0.1");
        auditService.record(ORG_ID, null, "INCIDENT_STATUS_CHANGE", "incident", 3L, "{\"c\":3}", "127.0.0.1");

        assertThat(auditService.verifyChain()).isNull();
    }

    @Test
    void detectsTamperedRow() {
        auditService.record(ORG_ID, null, "LOGIN", "user", 1L, "{\"a\":1}", "127.0.0.1");
        var tampered = auditService.record(ORG_ID, null, "RULE_CREATE", "detection_rule", 2L,
                "{\"b\":2}", "127.0.0.1");
        auditService.record(ORG_ID, null, "LOGOUT", "user", 1L, null, "127.0.0.1");

        assertThat(auditService.verifyChain()).isNull();

        // Tamper with the middle row's details, bypassing the service.
        jdbcTemplate.update("UPDATE audit_logs SET details = ? WHERE id = ?",
                "{\"b\":999}", tampered.getId());

        assertThat(auditService.verifyChain()).isEqualTo(tampered.getId());
    }
}
