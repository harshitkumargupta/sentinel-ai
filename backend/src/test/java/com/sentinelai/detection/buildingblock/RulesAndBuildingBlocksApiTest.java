package com.sentinelai.detection.buildingblock;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rules edited through the API really drive detection; building blocks gate rules; validation. */
class RulesAndBuildingBlocksApiTest extends IntegrationTestSupport {

    /** Each test uses its own time slice so window counts never leak between tests. */
    private static final AtomicLong CLOCK = new AtomicLong(Instant.parse("2026-06-01T00:00:00Z").getEpochSecond());

    @Autowired private IngestionService ingestionService;
    @Autowired private AlertRepository alertRepository;
    @Autowired private com.sentinelai.detection.repository.DetectionRuleRepository ruleRepository;

    private String admin;
    private long t0;

    @BeforeEach
    void setUp() throws Exception {
        admin = bearerFor("rules-admin", Role.ADMIN);
        alertRepository.deleteAll();
        ruleRepository.findAll().stream().filter(r -> r.getName().startsWith("t-"))
                .forEach(ruleRepository::delete);
        t0 = CLOCK.addAndGet(100_000);
    }

    private long createRule(String name, String config) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"ruleType\":\"BRUTE_FORCE\",\"severity\":\"HIGH\","
                + "\"mitreTechnique\":\"T1110\",\"config\":" + objectMapper.writeValueAsString(config) + "}";
        String res = mockMvc.perform(post("/api/rules").header("Authorization", admin)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).path("data").path("id").asLong();
    }

    private void failedLogins(String user, String ip, int n) {
        for (int i = 0; i < n; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("username", user);
            p.put("sourceIp", ip);
            p.put("eventTimestamp", Instant.ofEpochSecond(t0 + i).toString());
            ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
        }
        t0 += 10_000;
    }

    private long alertsFor(long ruleId) {
        return alertRepository.findAll().stream().filter(a -> Long.valueOf(ruleId).equals(a.getRuleId())).count();
    }

    @Test
    void buildingBlockRestrictsRuleToExternalSources() throws Exception {
        long rule = createRule("t-bf-external",
                "{\"threshold\":3,\"windowSeconds\":300,\"groupBy\":\"username\",\"buildingBlocks\":[\"External source IP\"]}");
        failedLogins("bb-internal", "10.20.1.5", 5);
        assertThat(alertsFor(rule)).isZero();
        failedLogins("bb-external", "45.33.1.2", 5);
        assertThat(alertsFor(rule)).isPositive();
    }

    @Test
    void editingAndDisablingRulesChangesDetection() throws Exception {
        long rule = createRule("t-bf-edit", "{\"threshold\":10,\"windowSeconds\":300,\"groupBy\":\"username\"}");
        failedLogins("edit-a", "45.33.1.3", 4);
        assertThat(alertsFor(rule)).isZero();

        mockMvc.perform(put("/api/rules/" + rule).header("Authorization", admin).contentType("application/json")
                        .content("{\"config\":\"{\\\"threshold\\\":3,\\\"windowSeconds\\\":300,\\\"groupBy\\\":\\\"username\\\"}\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        failedLogins("edit-b", "45.33.1.3", 4);
        long afterEdit = alertsFor(rule);
        assertThat(afterEdit).isPositive();

        mockMvc.perform(patch("/api/rules/" + rule + "/enabled").header("Authorization", admin)
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        failedLogins("edit-c", "45.33.1.3", 4);
        assertThat(alertsFor(rule)).isEqualTo(afterEdit);
    }

    @Test
    void ruleValidation() throws Exception {
        String[] bad = {
                "{\"name\":\"t-v1\",\"ruleType\":\"BRUTE_FORCE\",\"severity\":\"LOW\",\"config\":\"not json\"}",
                "{\"name\":\"t-v2\",\"ruleType\":\"BRUTE_FORCE\",\"severity\":\"LOW\",\"config\":\"{\\\"threshold\\\":0}\"}",
                "{\"name\":\"t-v3\",\"ruleType\":\"BRUTE_FORCE\",\"severity\":\"LOW\",\"config\":\"{\\\"groupBy\\\":\\\"planet\\\"}\"}",
                "{\"name\":\"t-v4\",\"ruleType\":\"BRUTE_FORCE\",\"severity\":\"LOW\",\"config\":\"{\\\"buildingBlocks\\\":[\\\"nope\\\"]}\"}",
                "{\"name\":\"t-v5\",\"ruleType\":\"MADE_UP\",\"severity\":\"LOW\",\"config\":\"{}\"}",
                "{\"name\":\"t-v6\",\"ruleType\":\"BRUTE_FORCE\",\"severity\":\"LOW\",\"config\":\"[1]\"}"};
        for (String body : bad) {
            mockMvc.perform(post("/api/rules").header("Authorization", admin).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/rules/types").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.hasItems("BRUTE_FORCE", "PORT_SCAN", "DDOS")));
    }

    @Test
    void buildingBlockCrudValidationAndProtection() throws Exception {
        String res = mockMvc.perform(post("/api/building-blocks").header("Authorization", admin)
                        .contentType("application/json")
                        .content("{\"name\":\"t-Corp VPN\",\"conditions\":[{\"field\":\"sourceIp\",\"op\":\"IN_CIDR\",\"values\":[\"100.64.0.0/10\"]}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode bb = objectMapper.readTree(res).path("data");
        long id = bb.path("id").asLong();

        mockMvc.perform(post("/api/building-blocks").header("Authorization", admin).contentType("application/json")
                        .content("{\"name\":\"t-bad\",\"conditions\":[{\"field\":\"sourceIp\",\"op\":\"IN_CIDR\",\"values\":[\"999.1.1.1/8\"]}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/building-blocks").header("Authorization", admin).contentType("application/json")
                        .content("{\"name\":\"t-bad2\",\"conditions\":[{\"field\":\"shoeSize\",\"op\":\"EQUALS\",\"values\":[\"9\"]}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/building-blocks").header("Authorization", admin).contentType("application/json")
                        .content("{\"name\":\"t-Corp VPN\",\"conditions\":[{\"field\":\"hour\",\"op\":\"IN\",\"values\":[\"1\"]}]}"))
                .andExpect(status().isConflict());

        createRule("t-uses-vpn", "{\"threshold\":3,\"buildingBlocks\":[\"t-Corp VPN\"]}");
        mockMvc.perform(delete("/api/building-blocks/" + id).header("Authorization", admin))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/building-blocks/" + id).header("Authorization", admin).contentType("application/json")
                        .content("{\"name\":\"t-renamed\",\"conditions\":[{\"field\":\"hour\",\"op\":\"IN\",\"values\":[\"1\"]}]}"))
                .andExpect(status().isConflict());

        String viewer = bearerFor("bb-viewer", Role.VIEWER);
        mockMvc.perform(get("/api/building-blocks").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='t-Corp VPN')].usedByRules[0]").value("t-uses-vpn"));
        mockMvc.perform(post("/api/building-blocks").header("Authorization", viewer).contentType("application/json")
                        .content("{\"name\":\"x\",\"conditions\":[{\"field\":\"hour\",\"op\":\"IN\",\"values\":[\"1\"]}]}"))
                .andExpect(status().isForbidden());

        ruleRepository.findAll().stream().filter(r -> r.getName().equals("t-uses-vpn")).forEach(ruleRepository::delete);
        mockMvc.perform(delete("/api/building-blocks/" + id).header("Authorization", admin)).andExpect(status().isOk());
    }
}
