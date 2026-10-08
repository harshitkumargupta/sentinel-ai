package com.sentinelai.offense;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.auth.domain.Role;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Offense list ranking, detail, magnitude components, notes and RBAC. */
class OffenseApiTest extends IntegrationTestSupport {

    @Autowired private IngestionService ingestionService;
    @Autowired private DetectionRuleRepository ruleRepository;

    private String analyst;

    @BeforeEach
    void setUp() throws Exception {
        if (!ruleRepository.existsByName("off-bf")) {
            ruleRepository.save(DetectionRule.builder().org(organizationRepository.findById(ORG_ID).orElseThrow())
                    .name("off-bf").ruleType("BRUTE_FORCE")
                    .config("{\"threshold\":3,\"windowSeconds\":3600,\"groupBy\":\"username\"}")
                    .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
        }
        analyst = bearerFor("off-analyst", Role.ANALYST);
        failedLogins("root", Instant.parse("2026-07-01T10:00:00Z"));      // privileged target
        failedLogins("jdoe-off", Instant.parse("2026-07-02T10:00:00Z"));  // ordinary user
    }

    private void failedLogins(String user, Instant base) {
        for (int i = 0; i < 4; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("username", user);
            p.put("sourceIp", "45.33.7.7");
            p.put("eventTimestamp", base.plusSeconds(i * 5L).toString());
            ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
        }
    }

    private JsonNode list(String query) throws Exception {
        String res = mockMvc.perform(get("/api/offenses" + query).header("Authorization", analyst))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).path("data");
    }

    @Test
    void listRanksByMagnitudeWithComponents() throws Exception {
        JsonNode page = list("");
        assertThat(page.path("totalElements").asInt()).isEqualTo(2);
        JsonNode top = page.path("content").get(0);
        assertThat(top.path("offenseSource").asText()).isEqualTo("user:root");
        JsonNode mag = top.path("magnitude");
        assertThat(mag.path("relevanceReasons").toString()).contains("privileged");
        assertThat(mag.path("magnitude").asInt())
                .isGreaterThanOrEqualTo(page.path("content").get(1).path("magnitude").path("magnitude").asInt());
        assertThat(mag.path("formula").asText()).startsWith("round((3*");
        assertThat(top.path("categories").toString()).contains("BRUTE_FORCE");
        assertThat(top.path("eventCount").asInt()).isEqualTo(4);

        assertThat(list("?minMagnitude=11").path("totalElements").asInt()).isZero();
        assertThat(list("?status=RESOLVED").path("totalElements").asInt()).isZero();
    }

    @Test
    void detailNotesAndFeedbackAffectCredibility() throws Exception {
        long id = list("").path("content").get(0).path("id").asLong();
        mockMvc.perform(get("/api/offenses/" + id).header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(4))
                .andExpect(jsonPath("$.data.offense.magnitude.credibility").value(3));

        mockMvc.perform(post("/api/offenses/" + id + "/notes").header("Authorization", analyst)
                        .contentType("application/json").content("{\"body\":\"Checked VPN logs \\u0007— nothing.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author").value("off-analyst"))
                .andExpect(jsonPath("$.data.body").value("Checked VPN logs — nothing."));
        mockMvc.perform(get("/api/offenses/" + id + "/notes").header("Authorization", analyst))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(patch("/api/incidents/" + id + "/feedback").header("Authorization", analyst)
                        .contentType("application/json").content("{\"feedback\":\"FALSE_POSITIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/offenses/" + id + "/magnitude").header("Authorization", analyst))
                .andExpect(jsonPath("$.data.credibility").value(0));
    }

    @Test
    void rbacValidationAndNotFound() throws Exception {
        long id = list("").path("content").get(0).path("id").asLong();
        String viewer = bearerFor("off-viewer", Role.VIEWER);
        mockMvc.perform(get("/api/offenses").header("Authorization", viewer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/offenses/" + id + "/notes").header("Authorization", viewer)
                        .contentType("application/json").content("{\"body\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/offenses/" + id + "/notes").header("Authorization", analyst)
                        .contentType("application/json").content("{\"body\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/offenses/999999").header("Authorization", analyst)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/offenses/assignees").header("Authorization", analyst))
                .andExpect(jsonPath("$.data[?(@.username=='off-analyst')]").exists())
                .andExpect(jsonPath("$.data[?(@.username=='off-viewer')]").doesNotExist());
    }
}
