package com.sentinelai.detection.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sandbox diff: current vs edited thresholds over a bundled dataset; nothing is persisted. */
class RuleSandboxApiTest extends IntegrationTestSupport {

    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private AlertRepository alertRepository;

    @Test
    void editedThresholdDiffOverDatasetWithoutSideEffects() throws Exception {
        String analyst = bearerFor("sb-analyst", Role.ANALYST);
        long id = ruleRepository.save(DetectionRule.builder().org(organizationRepository.findById(ORG_ID).orElseThrow())
                .name("sb-bf-" + System.nanoTime()).ruleType("BRUTE_FORCE")
                .config("{\"threshold\":20,\"windowSeconds\":300,\"groupBy\":\"username\"}")
                .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build()).getId();
        long alertsBefore = alertRepository.count();
        long eventsBefore = securityEventRepository.count();

        String res = mockMvc.perform(post("/api/rules/" + id + "/sandbox").header("Authorization", analyst)
                        .contentType("application/json")
                        .content("{\"editedConfig\":\"{\\\"threshold\\\":10,\\\"windowSeconds\\\":300,\\\"groupBy\\\":\\\"username\\\"}\",\"datasetId\":\"auth_log\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode r = objectMapper.readTree(res).path("data");
        assertThat(r.path("eventsScanned").asInt()).isEqualTo(32);
        assertThat(r.path("current").path("alerts").asInt()).isZero();
        assertThat(r.path("edited").path("alerts").asInt()).isEqualTo(5); // 10th..14th failed root login
        assertThat(r.path("newlyAlerting").toString()).contains("USERNAME:root");
        assertThat(r.path("edited").path("samples").get(0).path("events").size()).isPositive();

        assertThat(alertRepository.count()).isEqualTo(alertsBefore);      // no alerts created
        assertThat(securityEventRepository.count()).isEqualTo(eventsBefore); // dataset not ingested

        mockMvc.perform(post("/api/rules/" + id + "/sandbox").header("Authorization", analyst).contentType("application/json")
                .content("{\"editedConfig\":\"{\\\"threshold\\\":0}\",\"datasetId\":\"auth_log\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/rules/" + id + "/sandbox").header("Authorization", analyst).contentType("application/json")
                .content("{\"editedConfig\":\"{}\"}")).andExpect(status().isBadRequest());
        String viewer = bearerFor("sb-viewer", Role.VIEWER);
        mockMvc.perform(post("/api/rules/" + id + "/sandbox").header("Authorization", viewer).contentType("application/json")
                .content("{\"editedConfig\":\"{}\",\"datasetId\":\"auth_log\"}")).andExpect(status().isForbidden());
    }
}
