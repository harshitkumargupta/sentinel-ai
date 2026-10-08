package com.sentinelai.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.audit.domain.AuditLog;
import com.sentinelai.audit.repository.AuditLogRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Case management: status workflow, priority, assignee rules, filters, notes, merged timeline, audit. */
class CaseManagementApiTest extends IntegrationTestSupport {

    @Autowired private IngestionService ingestionService;
    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private String analyst;
    private String analyst2;
    private String admin;
    private String viewer;
    private User analystUser;
    private User viewerUser;
    private long caseId;

    @BeforeEach
    void setUp() throws Exception {
        if (!ruleRepository.existsByName("case-bf")) {
            ruleRepository.save(DetectionRule.builder().org(organizationRepository.findById(ORG_ID).orElseThrow())
                    .name("case-bf").ruleType("BRUTE_FORCE")
                    .config("{\"threshold\":3,\"windowSeconds\":3600,\"groupBy\":\"username\"}")
                    .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
        }
        analyst = bearerFor("case-analyst", Role.ANALYST);
        analyst2 = bearerFor("case-analyst2", Role.ANALYST);
        admin = bearerFor("case-admin", Role.ADMIN);
        viewer = bearerFor("case-viewer", Role.VIEWER);
        analystUser = userRepository.findByUsername("case-analyst").orElseThrow();
        viewerUser = userRepository.findByUsername("case-viewer").orElseThrow();
        Instant base = Instant.parse("2026-10-01T08:00:00Z");
        for (int i = 0; i < 4; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("username", "case-victim");
            p.put("sourceIp", "45.33.8.8");
            p.put("eventTimestamp", base.plusSeconds(i).toString());
            ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
        }
        caseId = incidentRepository.findAll().stream()
                .filter(i -> "user:case-victim".equals(i.getCorrelationKey())).findFirst().orElseThrow().getId();
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    private void moveTo(String token, String to, int expected) throws Exception {
        mockMvc.perform(patch("/api/incidents/" + caseId + "/status").header("Authorization", token)
                .contentType("application/json").content("{\"status\":\"" + to + "\"}")).andExpect(status().is(expected));
    }

    @Test
    void statusWorkflowEndsClosedAndIsTerminal() throws Exception {
        moveTo(analyst, "INVESTIGATING", 200);
        moveTo(analyst, "RESOLVED", 200);
        moveTo(analyst, "INVESTIGATING", 200); // reopen
        moveTo(analyst, "RESOLVED", 200);
        moveTo(analyst, "CLOSED", 200);
        assertThat(incidentRepository.findById(caseId).orElseThrow().getClosedAt()).isNotNull();
        moveTo(analyst, "INVESTIGATING", 409);
        moveTo(viewer, "INVESTIGATING", 403);
    }

    @Test
    void priorityDefaultsFromSeverityAndIsEditable() throws Exception {
        assertThat(incidentRepository.findById(caseId).orElseThrow().getPriority()).isNotNull();
        mockMvc.perform(patch("/api/incidents/" + caseId + "/priority").header("Authorization", analyst)
                        .contentType("application/json").content("{\"priority\":\"P1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.priority").value("P1"));
        mockMvc.perform(patch("/api/incidents/" + caseId + "/priority").header("Authorization", analyst)
                        .contentType("application/json").content("{\"priority\":\"P9\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/incidents/" + caseId + "/priority").header("Authorization", viewer)
                        .contentType("application/json").content("{\"priority\":\"P2\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/incidents?priority=P1").header("Authorization", viewer))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void assigneeMustBeAnalystOrAdminAndFiltersWork() throws Exception {
        mockMvc.perform(patch("/api/incidents/" + caseId + "/assign").header("Authorization", analyst)
                        .contentType("application/json").content("{\"assigneeId\":" + viewerUser.getId() + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/incidents?unassigned=true").header("Authorization", viewer))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(patch("/api/incidents/" + caseId + "/assign").header("Authorization", analyst)
                        .contentType("application/json").content("{\"assigneeId\":" + analystUser.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.assignedTo").value("case-analyst"));
        mockMvc.perform(get("/api/incidents?assigneeId=" + analystUser.getId() + "&status=OPEN").header("Authorization", viewer))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/incidents?unassigned=true").header("Authorization", viewer))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void notesEditDeletePermissionsAndMergedTimeline() throws Exception {
        long noteId = body(mockMvc.perform(post("/api/offenses/" + caseId + "/notes").header("Authorization", analyst)
                .contentType("application/json").content("{\"body\":\"First look: VPN brute force\"}"))).path("id").asLong();

        mockMvc.perform(put("/api/offenses/" + caseId + "/notes/" + noteId).header("Authorization", analyst2)
                        .contentType("application/json").content("{\"body\":\"hijack\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/offenses/" + caseId + "/notes/" + noteId).header("Authorization", analyst)
                        .contentType("application/json").content("{\"body\":\"Confirmed: VPN brute force\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("Confirmed: VPN brute force"))
                .andExpect(jsonPath("$.data.editedBy").value("case-analyst"));
        mockMvc.perform(delete("/api/offenses/" + caseId + "/notes/" + noteId).header("Authorization", viewer))
                .andExpect(status().isForbidden());

        long second = body(mockMvc.perform(post("/api/offenses/" + caseId + "/notes").header("Authorization", analyst)
                .contentType("application/json").content("{\"body\":\"temp\"}"))).path("id").asLong();
        mockMvc.perform(delete("/api/offenses/" + caseId + "/notes/" + second).header("Authorization", admin))
                .andExpect(status().isOk());

        moveTo(analyst, "INVESTIGATING", 200);
        mockMvc.perform(post("/api/incidents/" + caseId + "/actions").header("Authorization", analyst)
                        .contentType("application/json").content("{\"actionType\":\"block_ip\",\"target\":\"45.33.8.8\"}"))
                .andExpect(status().isOk());

        JsonNode tl = body(mockMvc.perform(get("/api/incidents/" + caseId + "/case-timeline").header("Authorization", viewer)));
        List<String> kinds = new java.util.ArrayList<>();
        List<String> titles = new java.util.ArrayList<>();
        tl.forEach(e -> { kinds.add(e.path("kind").asText()); titles.add(e.path("title").asText()); });
        assertThat(kinds).contains("DETECTION", "NOTE", "STATUS", "ACTION");
        assertThat(titles).contains("Case opened", "Status New → In Progress", "Note by case-analyst (edited)",
                "Note deleted", "Response proposed: block ip 45.33.8.8");
        assertThat(auditLogRepository.findByEntityTypeAndEntityId("incident", caseId)).extracting(AuditLog::getAction)
                .contains("OFFENSE_NOTE_ADD", "OFFENSE_NOTE_EDIT", "OFFENSE_NOTE_DELETE", "INCIDENT_STATUS_CHANGE");
    }
}
