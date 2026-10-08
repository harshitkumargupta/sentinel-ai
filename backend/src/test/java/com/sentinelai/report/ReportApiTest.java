package com.sentinelai.report;

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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every report type in both formats, downloads, validation, schedules (run-now and due runs), RBAC. */
class ReportApiTest extends IntegrationTestSupport {

    @Autowired private IngestionService ingestionService;
    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private ReportScheduleRepository scheduleRepository;
    @Autowired private ReportScheduleService scheduleService;

    private String analyst;
    private String admin;
    private Instant from;
    private Instant to;

    @BeforeEach
    void setUp() throws Exception {
        if (!ruleRepository.existsByName("rep-bf")) {
            ruleRepository.save(DetectionRule.builder().org(organizationRepository.findById(ORG_ID).orElseThrow())
                    .name("rep-bf").ruleType("BRUTE_FORCE").config("{\"threshold\":3,\"windowSeconds\":3600}")
                    .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
        }
        analyst = bearerFor("rep-analyst", Role.ANALYST);
        admin = bearerFor("rep-admin", Role.ADMIN);
        Instant now = Instant.now();
        for (int i = 0; i < 4; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("username", "rep-victim");
            p.put("sourceIp", "45.33.12.7");
            p.put("eventTimestamp", now.minusSeconds(60 - i).toString());
            ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
        }
        from = now.minus(1, ChronoUnit.DAYS);
        to = now.plus(1, ChronoUnit.HOURS);
    }

    private JsonNode generate(String token, String type, String format, Instant f, Instant t, int expected) throws Exception {
        String res = mockMvc.perform(post("/api/reports/generate").header("Authorization", token).contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"format\":\"" + format + "\",\"from\":\"" + f + "\",\"to\":\"" + t + "\"}"))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).path("data");
    }

    private byte[] download(long id) throws Exception {
        return mockMvc.perform(get("/api/reports/" + id + "/download").header("Authorization", analyst))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    @Test
    void everyTypeRendersInBothFormats() throws Exception {
        for (ReportType type : ReportType.values()) {
            for (ReportFormat format : ReportFormat.values()) {
                JsonNode r = generate(analyst, type.name(), format.name(), from, to, 200);
                assertThat(r.path("status").asText()).as(type + " " + format).isEqualTo("COMPLETED");
                byte[] file = download(r.path("id").asLong());
                if (format == ReportFormat.PDF) {
                    assertThat(new String(file, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
                } else {
                    assertThat(new String(file, StandardCharsets.UTF_8)).contains("# " + type.title());
                }
            }
        }
        String incidents = new String(download(generate(analyst, "INCIDENT_SUMMARY", "CSV", from, to, 200).path("id").asLong()),
                StandardCharsets.UTF_8);
        assertThat(incidents).contains("rep-victim").contains("# Incidents opened: 1");
        String attackers = new String(download(generate(analyst, "TOP_ATTACKERS", "CSV", from, to, 200).path("id").asLong()),
                StandardCharsets.UTF_8);
        assertThat(attackers).contains("45.33.12.7").contains("yes"); // on the sample threat-intel list
        mockMvc.perform(get("/api/reports").header("Authorization", analyst))
                .andExpect(jsonPath("$.data.length()").value(10))
                .andExpect(jsonPath("$.data[0].content").doesNotExist());
    }

    @Test
    void validationAndRbac() throws Exception {
        generate(analyst, "INCIDENT_SUMMARY", "PDF", to, from, 400);
        generate(analyst, "INCIDENT_SUMMARY", "PDF", from.minus(400, ChronoUnit.DAYS), to, 400);
        generate(analyst, "NOT_A_TYPE", "PDF", from, to, 400);
        String viewer = bearerFor("rep-viewer", Role.VIEWER);
        generate(viewer, "INCIDENT_SUMMARY", "PDF", from, to, 403);
        long id = generate(analyst, "INCIDENT_SUMMARY", "CSV", from, to, 200).path("id").asLong();
        mockMvc.perform(delete("/api/reports/" + id).header("Authorization", analyst)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/reports/" + id).header("Authorization", admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/" + id + "/download").header("Authorization", analyst)).andExpect(status().isNotFound());
    }

    @Test
    void schedulesRunNowAndWhenDue() throws Exception {
        String body = "{\"name\":\"Weekly exec\",\"type\":\"ALERTS_BY_MITRE\",\"format\":\"PDF\",\"frequency\":\"WEEKLY\",\"dayOfWeek\":1,\"hourUtc\":7}";
        mockMvc.perform(post("/api/reports/schedules").header("Authorization", analyst).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/reports/schedules").header("Authorization", admin).contentType("application/json")
                        .content(body.replace(",\"dayOfWeek\":1", "")))
                .andExpect(status().isBadRequest());
        String res = mockMvc.perform(post("/api/reports/schedules").header("Authorization", admin).contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long sid = objectMapper.readTree(res).path("data").path("id").asLong();
        assertThat(objectMapper.readTree(res).path("data").path("nextRunAt").asText()).isNotBlank();

        mockMvc.perform(post("/api/reports/schedules/" + sid + "/run").header("Authorization", analyst))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.scheduleId").value(sid))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        ReportSchedule s = scheduleRepository.findById(sid).orElseThrow();
        s.setNextRunAt(Instant.now().minusSeconds(5));
        scheduleRepository.save(s);
        assertThat(scheduleService.runDue()).isEqualTo(1);
        ReportSchedule after = scheduleRepository.findById(sid).orElseThrow();
        assertThat(after.getLastRunAt()).isNotNull();
        assertThat(after.getNextRunAt()).isAfter(Instant.now());
        assertThat(scheduleService.runDue()).isZero(); // not due again
        scheduleRepository.deleteAll();
    }
}
