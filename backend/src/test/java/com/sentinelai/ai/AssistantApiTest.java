package com.sentinelai.ai;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Ask AI" over HTTP: fixed intents, free-text routing, validation, tenancy and the offline badge. */
class AssistantApiTest extends IntegrationTestSupport {

    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private IngestionService ingestionService;

    private Incident incident;
    private String viewer;

    @BeforeEach
    void setUp() throws Exception {
        if (!ruleRepository.existsByName("ask-bf")) {
            ruleRepository.save(DetectionRule.builder().org(organizationRepository.findById(ORG_ID).orElseThrow())
                    .name("ask-bf").ruleType("BRUTE_FORCE")
                    .config("{\"threshold\":3,\"windowSeconds\":3600,\"groupBy\":\"username\"}")
                    .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
        }
        Instant base = Instant.parse("2026-05-01T10:00:00Z");
        for (int i = 0; i < 4; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("username", "askvictim");
            p.put("sourceIp", "45.33.9.9");
            p.put("geoCountry", "CN");
            p.put("eventTimestamp", base.plusSeconds(i * 5L).toString());
            ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
        }
        incident = incidentRepository.findAll().stream()
                .filter(i -> "user:askvictim".equals(i.getCorrelationKey())).findFirst().orElseThrow();
        viewer = bearerFor("ask-viewer", Role.VIEWER);
    }

    @Test
    void answersFixedIntentAndFreeText() throws Exception {
        mockMvc.perform(post("/api/incidents/" + incident.getId() + "/ask").header("Authorization", viewer)
                        .contentType("application/json").content("{\"intent\":\"WHAT_HAPPENED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("WHAT_HAPPENED"))
                .andExpect(jsonPath("$.data.offline").value(true))
                .andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("failed login")));

        mockMvc.perform(post("/api/incidents/" + incident.getId() + "/ask").header("Authorization", viewer)
                        .contentType("application/json").content("{\"question\":\"Which IPs are involved?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("WHICH_IPS"))
                .andExpect(jsonPath("$.data.bullets[0]").value(org.hamcrest.Matchers.startsWith("45.33.9.9")));
    }

    @Test
    void rejectsOversizedQuestionAndUnknownIncident() throws Exception {
        String longQ = "x".repeat(301);
        mockMvc.perform(post("/api/incidents/" + incident.getId() + "/ask").header("Authorization", viewer)
                        .contentType("application/json").content("{\"question\":\"" + longQ + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/incidents/999999/ask").header("Authorization", viewer)
                        .contentType("application/json").content("{\"intent\":\"WHAT_HAPPENED\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/incidents/" + incident.getId() + "/ask")
                        .contentType("application/json").content("{\"intent\":\"WHAT_HAPPENED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statusReportsOfflineMode() throws Exception {
        mockMvc.perform(get("/api/ai/status").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offline").value(true))
                .andExpect(jsonPath("$.data.label").value("Offline mode"));
        mockMvc.perform(get("/api/ai/questions").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5));
    }
}
