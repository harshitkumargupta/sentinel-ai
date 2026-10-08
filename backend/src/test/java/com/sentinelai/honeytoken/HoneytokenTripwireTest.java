package com.sentinelai.honeytoken;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.alert.domain.Alert;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.honeytoken.repository.HoneytokenRepository;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Decoy username / API key / URL path: each touch raises a CRITICAL "decoy touched" alert + incident. */
class HoneytokenTripwireTest extends IntegrationTestSupport {

    @Autowired private HoneytokenRepository honeytokens;
    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private AlertRepository alertRepository;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        alertRepository.deleteAll();
        honeytokens.deleteAll();
        if (ruleRepository.findAll().stream().noneMatch(r -> r.getRuleType().equals("HONEYTOKEN") && r.isEnabled())) {
            ruleRepository.save(DetectionRule.builder().org(organizationRepository.findById(ORG_ID).orElseThrow())
                    .name("ht-rule").ruleType("HONEYTOKEN").config("{}").enabled(true)
                    .severity(Severity.CRITICAL).mitreTechnique("T1078.001").version(1).build());
        }
        admin = bearerFor("ht-admin", Role.ADMIN);
    }

    private JsonNode create(String kind, String value) throws Exception {
        String body = value == null ? "{\"kind\":\"" + kind + "\",\"description\":\"decoy\"}"
                : "{\"kind\":\"" + kind + "\",\"value\":\"" + value + "\",\"description\":\"decoy\"}";
        String res = mockMvc.perform(post("/api/honeytokens").header("Authorization", admin).contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(res).path("data");
    }

    private List<Alert> decoyAlerts() {
        return alertRepository.findAll().stream().filter(a -> a.getRuleType().equals("HONEYTOKEN")).toList();
    }

    @Test
    void decoyUsernameLoginTripsCriticalIncident() throws Exception {
        create("USERNAME", "svc_backup");
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"svc_backup\",\"password\":\"Whatever@123\"}")).andExpect(status().isUnauthorized());
        assertThat(decoyAlerts()).singleElement().satisfies(a -> {
            assertThat(a.getSeverity()).isEqualTo(Severity.CRITICAL);
            assertThat(a.getMessage()).startsWith("Decoy touched: USERNAME svc_backup");
        });
        assertThat(incidentRepository.findAll()).anyMatch(i -> i.getTitle().startsWith("Decoy touched"));
        assertThat(honeytokens.findAll().get(0).getTriggeredCount()).isEqualTo(1);
    }

    @Test
    void decoyApiKeyAndDecoyPathTripAndRevealNothing() throws Exception {
        JsonNode key = create("API_KEY", null);
        String secret = key.path("secret").asText();
        assertThat(secret).startsWith("sk_");
        assertThat(key.path("decoy").path("displayValue").asText()).contains("…").doesNotContain(secret);
        mockMvc.perform(post("/api/ingest/events").header("X-API-Key", secret).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(decoyAlerts()).hasSize(1);

        create("URL_PATH", "/api/admin/backup-export");
        mockMvc.perform(get("/api/admin/backup-export")).andExpect(status().isNotFound());
        assertThat(decoyAlerts()).hasSize(2);
        assertThat(decoyAlerts()).anyMatch(a -> a.getMessage().contains("URL_PATH /api/admin/backup-export"));
    }

    @Test
    void testButtonValidationAndRbac() throws Exception {
        long id = create("USERNAME", "old_admin_2019").path("decoy").path("id").asLong();
        mockMvc.perform(post("/api/honeytokens/" + id + "/test").header("Authorization", admin)).andExpect(status().isOk());
        assertThat(decoyAlerts()).hasSize(1);
        long incident = incidentRepository.findAll().stream().filter(i -> i.getTitle().startsWith("Decoy touched")).findFirst().orElseThrow().getId();
        String tl = mockMvc.perform(get("/api/incidents/" + incident + "/case-timeline").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(tl).contains("Decoy touched (honeytoken)");

        mockMvc.perform(post("/api/honeytokens").header("Authorization", admin).contentType("application/json")
                .content("{\"kind\":\"URL_PATH\",\"value\":\"no-slash\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/honeytokens").header("Authorization", admin).contentType("application/json")
                .content("{\"kind\":\"USERNAME\",\"value\":\"old_admin_2019\"}")).andExpect(status().isConflict());
        String analyst = bearerFor("ht-analyst", Role.ANALYST);
        mockMvc.perform(post("/api/honeytokens").header("Authorization", analyst).contentType("application/json")
                .content("{\"kind\":\"USERNAME\",\"value\":\"x_decoy\"}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/honeytokens").header("Authorization", analyst)).andExpect(status().isOk());
    }
}
