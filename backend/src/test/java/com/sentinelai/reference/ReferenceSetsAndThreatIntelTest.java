package com.sentinelai.reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.buildingblock.BuildingBlock;
import com.sentinelai.detection.buildingblock.BuildingBlockMatcher;
import com.sentinelai.detection.buildingblock.BuildingBlockRepository;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.support.IntegrationTestSupport;
import com.sentinelai.threatintel.ThreatIntelService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reference sets drive rules via building blocks; offline threat intel loads and raises credibility. */
class ReferenceSetsAndThreatIntelTest extends IntegrationTestSupport {

    private static final AtomicLong CLOCK = new AtomicLong(Instant.parse("2026-09-01T00:00:00Z").getEpochSecond());

    @Autowired private IngestionService ingestionService;
    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private BuildingBlockRepository buildingBlockRepository;
    @Autowired private BuildingBlockMatcher matcher;
    @Autowired private AlertRepository alertRepository;
    @Autowired private ThreatIntelService threatIntel;

    private String admin;
    private String analyst;

    @BeforeEach
    void setUp() throws Exception {
        admin = bearerFor("ref-admin", Role.ADMIN);
        analyst = bearerFor("ref-analyst", Role.ANALYST);
        alertRepository.deleteAll();
        ruleRepository.findAll().stream().filter(r -> r.getName().startsWith("ref-")).forEach(ruleRepository::delete);
        buildingBlockRepository.findAll().stream().filter(b -> b.getName().startsWith("ref-"))
                .forEach(buildingBlockRepository::delete);
        matcher.evict();
    }

    private long setId(String name) throws Exception {
        String res = mockMvc.perform(get("/api/reference-sets").header("Authorization", analyst))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode s : objectMapper.readTree(res).path("data")) {
            if (s.path("name").asText().equals(name)) {
                return s.path("id").asLong();
            }
        }
        throw new AssertionError("no set " + name);
    }

    private void ruleWithBlock(String blockName, String conditionsJson) {
        var org = organizationRepository.findById(ORG_ID).orElseThrow();
        buildingBlockRepository.save(BuildingBlock.builder().org(org).name(blockName).conditions(conditionsJson).build());
        ruleRepository.save(DetectionRule.builder().org(org).name("ref-rule-" + blockName).ruleType("BRUTE_FORCE")
                .config("{\"threshold\":3,\"windowSeconds\":300,\"groupBy\":\"username\",\"buildingBlocks\":[\"" + blockName + "\"]}")
                .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
        matcher.evict();
    }

    private void failedLogins(String user, String ip) {
        long t0 = CLOCK.addAndGet(10_000);
        for (int i = 0; i < 4; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("username", user);
            p.put("sourceIp", ip);
            p.put("eventTimestamp", Instant.ofEpochSecond(t0 + i).toString());
            ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
        }
    }

    private long alerts(String ruleName) {
        Long id = ruleRepository.findAll().stream().filter(r -> r.getName().equals(ruleName)).findFirst().orElseThrow().getId();
        return alertRepository.findAll().stream().filter(a -> id.equals(a.getRuleId())).count();
    }

    @Test
    void watchlistItemsValidateAndDriveARule() throws Exception {
        long watch = setId("Watchlist IPs");
        mockMvc.perform(post("/api/reference-sets/" + watch + "/items").header("Authorization", analyst)
                        .contentType("application/json")
                        .content("{\"values\":[\"203.0.113.77\",\"192.168.50.0/24\",\"not-an-ip\",\"203.0.113.77\"],\"note\":\"t\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.added").value(2))
                .andExpect(jsonPath("$.data.invalid[0]").value("not-an-ip"));

        ruleWithBlock("ref-watched", "[{\"field\":\"sourceIp\",\"op\":\"IN_REFERENCE_SET\",\"values\":[\"Watchlist IPs\"]}]");
        failedLogins("ref-u1", "198.18.0.1");      // not listed
        assertThat(alerts("ref-rule-ref-watched")).isZero();
        failedLogins("ref-u2", "203.0.113.77");    // exact entry
        failedLogins("ref-u3", "192.168.50.12");   // inside listed CIDR
        assertThat(alerts("ref-rule-ref-watched")).isGreaterThanOrEqualTo(2);

        String viewer = bearerFor("ref-viewer", Role.VIEWER);
        mockMvc.perform(post("/api/reference-sets/" + watch + "/items").header("Authorization", viewer)
                        .contentType("application/json").content("{\"values\":[\"1.1.1.1\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void setLifecycleAndProtection() throws Exception {
        String res = mockMvc.perform(post("/api/reference-sets").header("Authorization", admin).contentType("application/json")
                        .content("{\"name\":\"ref-vip users\",\"elementType\":\"USERNAME\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(res).path("data").path("id").asLong();
        mockMvc.perform(post("/api/reference-sets").header("Authorization", admin).contentType("application/json")
                        .content("{\"name\":\"TI: fake\",\"elementType\":\"IP\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/reference-sets").header("Authorization", analyst).contentType("application/json")
                        .content("{\"name\":\"x\",\"elementType\":\"IP\"}"))
                .andExpect(status().isForbidden());

        ruleWithBlock("ref-vip", "[{\"field\":\"username\",\"op\":\"IN_REFERENCE_SET\",\"values\":[\"ref-vip users\"]}]");
        mockMvc.perform(delete("/api/reference-sets/" + id).header("Authorization", admin)).andExpect(status().isConflict());
    }

    @Test
    void threatIntelListsLoadMatchAndRaiseOffenseCredibility() throws Exception {
        assertThat(threatIntel.feeds()).extracting(ThreatIntelService.Feed::id)
                .contains("sample-bruteforce-sources", "sample-known-bad-networks");
        mockMvc.perform(get("/api/threat-intel/check?ip=198.51.100.42").header("Authorization", analyst))
                .andExpect(jsonPath("$.data.matches[0].list").value("Sample known-bad networks"));
        mockMvc.perform(get("/api/threat-intel/check?ip=not-ip").header("Authorization", analyst))
                .andExpect(status().isBadRequest());

        ruleWithBlock("ref-ti", "[{\"field\":\"sourceIp\",\"op\":\"IN_REFERENCE_SET\",\"values\":[\"TI:sample-bruteforce-sources\"]}]");
        failedLogins("ref-ti-victim", "45.33.12.7");
        assertThat(alerts("ref-rule-ref-ti")).isPositive();

        long incident = incidentRepository.findAll().stream()
                .filter(i -> "user:ref-ti-victim".equals(i.getCorrelationKey())).findFirst().orElseThrow().getId();
        mockMvc.perform(get("/api/offenses/" + incident).header("Authorization", analyst))
                .andExpect(jsonPath("$.data.threatIntel[0].ip").value("45.33.12.7"))
                .andExpect(jsonPath("$.data.offense.magnitude.credibilityReasons").value(
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("threat-intel"))));
    }
}
