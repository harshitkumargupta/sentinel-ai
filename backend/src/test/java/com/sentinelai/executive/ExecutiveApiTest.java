package com.sentinelai.executive;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Executive metrics: posture score + formula, MTTR, open vs resolved, severity series, top risk, summary, PDF. */
class ExecutiveApiTest extends IntegrationTestSupport {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void metricsScoreSummaryAndPdf() throws Exception {
        // other test classes leave coverage runs / vulnerabilities behind; the score depends on both
        jdbc.update("delete from coverage_runs");
        jdbc.update("delete from vulnerabilities");
        jdbc.update("insert into incidents (org_id,title,status,severity,risk_score,created_at) values (1,'Root brute force','OPEN','CRITICAL',90,now(6) - interval 2 hour)");
        jdbc.update("insert into incidents (org_id,title,status,severity,risk_score,created_at) values (1,'Port scan','OPEN','HIGH',60,now(6) - interval 1 hour)");
        jdbc.update("insert into incidents (org_id,title,status,severity,risk_score,created_at,resolved_at) values (1,'Old phish','RESOLVED','MEDIUM',30,now(6) - interval 3 hour,now(6) - interval 1 hour)");
        String viewer = bearerFor("exec-viewer", Role.VIEWER);

        JsonNode s = objectMapper.readTree(mockMvc.perform(get("/api/executive").param("days", "7").header("Authorization", viewer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(s.path("incidents").asInt()).isEqualTo(3);
        assertThat(s.path("open").asInt()).isEqualTo(2);
        assertThat(s.path("resolved").asInt()).isEqualTo(1);
        assertThat(s.path("mttrHours").asDouble()).isEqualTo(2.0);
        // 100 − (critical 10 + high 5 + MTTR 0 + no coverage run 10 + vulns 0 + unassigned 2) = 73
        assertThat(s.path("postureScore").asInt()).isEqualTo(73);
        assertThat(s.path("grade").asText()).isEqualTo("Fair");
        assertThat(s.path("formula").asText()).endsWith("= 73");
        assertThat(s.path("topRisks").get(0).path("title").asText()).isEqualTo("Root brute force");
        assertThat(s.path("bySeverity").size()).isEqualTo(8);
        assertThat(s.path("plainLanguage").toString()).contains("fair (73 out of 100)").contains("2 are still open")
                .contains("nobody is assigned");

        byte[] pdf = mockMvc.perform(get("/api/executive/report.pdf").header("Authorization", viewer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        mockMvc.perform(get("/api/executive")).andExpect(status().isUnauthorized());
    }

    @Test
    void plainLanguageWithNoData() {
        List<String> lines = ExecutiveService.plainLanguage(30, 90, "Strong", 0, 0, 0, 0, null, null, null, 0, 0, List.of(),
                List.of(new ExecutiveService.Penalty("x", 0, "")));
        assertThat(lines).contains("No security incidents were raised in this period.")
                .anyMatch(l -> l.contains("hasn't been run yet")).noneMatch(l -> l.contains("quickest way"));
    }
}
