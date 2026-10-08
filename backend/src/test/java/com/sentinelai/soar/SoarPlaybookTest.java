package com.sentinelai.soar;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.audit.domain.AuditLog;
import com.sentinelai.audit.repository.AuditLogRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.playbook.domain.PlaybookActionStatus;
import com.sentinelai.reference.ReferenceSetItemRepository;
import com.sentinelai.reference.ReferenceSetRepository;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Default Brute Force Response playbook: matching, run, steps' effects, approval still required, audit. */
class SoarPlaybookTest extends IntegrationTestSupport {

    @Autowired private IngestionService ingestionService;
    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private SoarPlaybookRepository playbooks;
    @Autowired private ReferenceSetRepository sets;
    @Autowired private ReferenceSetItemRepository items;
    @Autowired private AuditLogRepository auditLogs;

    private String analyst;
    private long incidentId;

    @BeforeEach
    void setUp() throws Exception {
        if (!ruleRepository.existsByName("soar-bf")) {
            ruleRepository.save(DetectionRule.builder().org(organizationRepository.findById(ORG_ID).orElseThrow())
                    .name("soar-bf").ruleType("BRUTE_FORCE").config("{\"threshold\":3,\"windowSeconds\":3600}")
                    .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
        }
        analyst = bearerFor("soar-analyst", Role.ANALYST);
        for (int i = 0; i < 4; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("username", "soar-victim");
            p.put("sourceIp", "45.33.66.6");
            p.put("eventTimestamp", Instant.parse("2026-10-05T00:00:00Z").plusSeconds(i).toString());
            ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
        }
        incidentId = incidentRepository.findAll().stream().filter(i -> "user:soar-victim".equals(i.getCorrelationKey()))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void bruteForceResponseRunsStepsAndOnlyProposesActions() throws Exception {
        long pbId = playbooks.findAll().stream().filter(p -> p.getName().equals("Brute Force Response")).findFirst().orElseThrow().getId();
        mockMvc.perform(get("/api/incidents/" + incidentId + "/playbooks").header("Authorization", analyst))
                .andExpect(jsonPath("$.data[?(@.name=='Brute Force Response')]").exists())
                .andExpect(jsonPath("$.data[?(@.name=='Data Exfiltration Response')]").doesNotExist());

        String res = mockMvc.perform(post("/api/soar/playbooks/" + pbId + "/run").header("Authorization", analyst)
                        .contentType("application/json").content("{\"incidentId\":" + incidentId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode run = objectMapper.readTree(res).path("data");
        assertThat(run.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(run.path("steps").size()).isEqualTo(4);

        var incident = incidentRepository.findById(incidentId).orElseThrow();
        assertThat(incident.getStatus().name()).isEqualTo("INVESTIGATING");
        assertThat(incident.getPriority().name()).isEqualTo("P2");
        assertThat(playbookActionRepository.findAll()).anyMatch(a -> a.getActionType().equals("block_ip")
                && a.getTargetRef().equals("45.33.66.6") && a.getStatus() == PlaybookActionStatus.PROPOSED);
        long watch = sets.findByOrg_IdAndName(ORG_ID, "Watchlist IPs").orElseThrow().getId();
        assertThat(items.existsBySet_IdAndValue(watch, "45.33.66.6")).isTrue();
        assertThat(auditLogs.findByEntityTypeAndEntityId("incident", incidentId)).extracting(AuditLog::getAction)
                .contains("SOAR_PLAYBOOK_RUN");
        mockMvc.perform(get("/api/soar/runs").header("Authorization", analyst))
                .andExpect(jsonPath("$.data[0].incidentId").value(incidentId));

        // Running again is idempotent for proposals (no duplicate live action).
        mockMvc.perform(post("/api/soar/playbooks/" + pbId + "/run").header("Authorization", analyst)
                .contentType("application/json").content("{\"incidentId\":" + incidentId + "}")).andExpect(status().isOk());
        assertThat(playbookActionRepository.findAll().stream().filter(a -> a.getActionType().equals("block_ip")).count()).isEqualTo(1);
    }

    @Test
    void validationAndRbac() throws Exception {
        String admin = bearerFor("soar-admin", Role.ADMIN);
        String viewer = bearerFor("soar-viewer", Role.VIEWER);
        mockMvc.perform(post("/api/soar/playbooks").header("Authorization", admin).contentType("application/json")
                        .content("{\"name\":\"no trigger\",\"steps\":[{\"type\":\"NOTIFY\"}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/soar/playbooks").header("Authorization", admin).contentType("application/json")
                        .content("{\"name\":\"bad\",\"triggerMinSeverity\":\"HIGH\",\"steps\":[{\"type\":\"ADD_TO_WATCHLIST\"}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/soar/playbooks").header("Authorization", analyst).contentType("application/json")
                        .content("{\"name\":\"x\",\"triggerMinSeverity\":\"HIGH\",\"steps\":[{\"type\":\"NOTIFY\"}]}"))
                .andExpect(status().isForbidden());
        long pbId = playbooks.findAll().get(0).getId();
        mockMvc.perform(post("/api/soar/playbooks/" + pbId + "/run").header("Authorization", viewer)
                .contentType("application/json").content("{\"incidentId\":" + incidentId + "}")).andExpect(status().isForbidden());
    }
}
