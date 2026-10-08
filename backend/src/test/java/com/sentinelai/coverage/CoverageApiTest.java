package com.sentinelai.coverage;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.util.Hashing;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.honeytoken.domain.Honeytoken;
import com.sentinelai.honeytoken.repository.HoneytokenRepository;
import com.sentinelai.simulator.scenario.HoneytokenScenario;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Coverage = detected ÷ tested scenarios; disabled rule → missed + gap; MITRE matrix; export; history. */
class CoverageApiTest extends IntegrationTestSupport {

    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private HoneytokenRepository honeytokenRepository;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = bearerFor("cov-admin", Role.ADMIN);
        ruleRepository.findAll().stream().filter(r -> r.getName().startsWith("cov-")).forEach(ruleRepository::delete);
        ruleRepository.findAll().forEach(r -> { r.setEnabled(false); ruleRepository.save(r); });
        var org = organizationRepository.findById(ORG_ID).orElseThrow();
        rule(org, "cov-bf", "BRUTE_FORCE", "{\"threshold\":10,\"windowSeconds\":300,\"groupBy\":\"username\"}", "T1110", true);
        rule(org, "cov-cs", "CREDENTIAL_STUFFING", "{\"distinctUsers\":5,\"windowSeconds\":300}", "T1110.004", true);
        rule(org, "cov-it", "IMPOSSIBLE_TRAVEL", "{\"minSecondsBetweenCountries\":3600}", "T1078", true);
        rule(org, "cov-aa", "ABNORMAL_ACCESS", "{}", "T1548", false); // deliberately disabled → gap
        rule(org, "cov-api", "HIGH_FREQUENCY_API", "{\"threshold\":100,\"windowSeconds\":60,\"groupBy\":\"sourceIp\"}", "T1499", true);
        rule(org, "cov-ht", "HONEYTOKEN", "{}", "T1078.001", true);
        String hash = Hashing.sha256Hex(HoneytokenScenario.DECOY_VALUE);
        if (honeytokenRepository.findFirstByOrg_IdAndValueHash(ORG_ID, hash).isEmpty()) {
            honeytokenRepository.save(Honeytoken.builder().org(org).type("AWS_ACCESS_KEY").valueHash(hash).triggeredCount(0).build());
        }
    }

    private void rule(com.sentinelai.common.domain.Organization org, String name, String type, String cfg, String mitre, boolean on) {
        ruleRepository.save(DetectionRule.builder().org(org).name(name).ruleType(type).config(cfg).enabled(on)
                .severity(Severity.HIGH).mitreTechnique(mitre).version(1).build());
    }

    @Test
    void runScoresMapsAndExplainsGaps() throws Exception {
        String res = mockMvc.perform(post("/api/coverage/run").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode run = objectMapper.readTree(res).path("data");
        JsonNode report = run.path("report");
        int tested = run.path("tested").asInt();
        int detected = run.path("detected").asInt();
        assertThat(tested).isEqualTo(6); // suspicious_login is time-dependent and skipped
        assertThat(detected).isLessThan(tested);
        assertThat(run.path("coveragePct").asDouble()).isEqualTo(Math.round(1000.0 * detected / tested) / 10.0);

        JsonNode abnormal = null;
        for (JsonNode s : report.path("scenarios")) {
            if (s.path("scenario").asText().equals("abnormal_access")) {
                abnormal = s;
            }
            if (s.path("scenario").asText().equals("brute_force")) {
                assertThat(s.path("detected").asBoolean()).isTrue();
                assertThat(s.path("timeToDetectMs").isNumber()).isTrue();
            }
        }
        assertThat(abnormal).isNotNull();
        assertThat(abnormal.path("detected").asBoolean()).isFalse();
        assertThat(report.path("gaps").toString()).contains("is disabled").contains("Enable it on the Rules page");

        String matrix = report.path("matrix").toString();
        assertThat(matrix).contains("\"technique\":\"T1110\",\"name\":\"Brute Force\",\"tactic\":\"Credential Access\",\"status\":\"DETECTED\"");
        assertThat(matrix).contains("\"technique\":\"T1548\"").contains("\"status\":\"MISSED\"");
        assertThat(matrix).contains("\"technique\":\"T1046\"").contains("\"status\":\"UNTESTED\"");

        long id = run.path("id").asLong();
        byte[] pdf = mockMvc.perform(get("/api/coverage/runs/" + id + "/export?format=pdf").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String csv = mockMvc.perform(get("/api/coverage/runs/" + id + "/export?format=csv").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("# Detection Coverage").contains("Abnormal Access").contains("MISSED");
        mockMvc.perform(get("/api/coverage/runs").header("Authorization", admin)).andExpect(jsonPath("$.data[0].id").value(id));

        String analyst = bearerFor("cov-analyst", Role.ANALYST);
        mockMvc.perform(post("/api/coverage/run").header("Authorization", analyst)).andExpect(status().isForbidden());
        // restore defaults for other tests
        ruleRepository.findAll().stream().filter(r -> !r.getName().startsWith("cov-")).forEach(r -> { r.setEnabled(true); ruleRepository.save(r); });
    }
}
