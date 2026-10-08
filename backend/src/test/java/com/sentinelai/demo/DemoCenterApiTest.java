package com.sentinelai.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.honeytoken.domain.Honeytoken;
import com.sentinelai.honeytoken.repository.HoneytokenRepository;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.simulator.scenario.HoneytokenScenario;
import com.sentinelai.support.IntegrationTestSupport;
import com.sentinelai.common.util.Hashing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Demo Center endpoints: every chain scenario produces real alerts + incidents; seed; reset; RBAC. */
@TestPropertySource(properties = {"sentinel.demo.enabled=true", "sentinel.demo.quick-login=true",
        "sentinel.demo.baseline-events=200"})
class DemoCenterApiTest extends IntegrationTestSupport {

    @Autowired private DetectionRuleRepository ruleRepository;
    @Autowired private HoneytokenRepository honeytokenRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private IngestionService ingestionService;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = bearerFor("demo-admin", Role.ADMIN);
        var org = organizationRepository.findById(ORG_ID).orElseThrow();
        rule(org, "demo-bf", "BRUTE_FORCE", "{\"threshold\":10,\"windowSeconds\":300,\"groupBy\":\"username\"}", Severity.HIGH, "T1110");
        rule(org, "demo-cs", "CREDENTIAL_STUFFING", "{\"distinctUsers\":5,\"windowSeconds\":300}", Severity.HIGH, "T1110.004");
        rule(org, "demo-it", "IMPOSSIBLE_TRAVEL", "{\"minSecondsBetweenCountries\":3600}", Severity.HIGH, "T1078");
        rule(org, "demo-aa", "ABNORMAL_ACCESS", "{}", Severity.HIGH, "T1548");
        rule(org, "demo-api", "HIGH_FREQUENCY_API", "{\"threshold\":100,\"windowSeconds\":60,\"groupBy\":\"sourceIp\"}", Severity.MEDIUM, "T1499");
        rule(org, "demo-ht", "HONEYTOKEN", "{}", Severity.CRITICAL, "T1078.001");
        String hash = Hashing.sha256Hex(HoneytokenScenario.DECOY_VALUE);
        if (honeytokenRepository.findByValueHash(hash).isEmpty()) {
            honeytokenRepository.save(Honeytoken.builder().org(org).type("AWS_ACCESS_KEY").valueHash(hash)
                    .description("demo test decoy").triggeredCount(0).build());
        }
        mockMvc.perform(post("/api/demo/reset").header("Authorization", admin)).andExpect(status().isOk());
    }

    private void rule(com.sentinelai.common.domain.Organization org, String name, String type, String config,
                      Severity severity, String mitre) {
        if (!ruleRepository.existsByName(name)) {
            ruleRepository.save(DetectionRule.builder().org(org).name(name).ruleType(type).config(config)
                    .enabled(true).severity(severity).mitreTechnique(mitre).version(1).build());
        }
    }

    private JsonNode postJson(String url) throws Exception {
        String body = mockMvc.perform(post(url).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    @Test
    void everyChainScenarioRaisesAlertsAndIncidentsLive() throws Exception {
        JsonNode chain = objectMapper.readTree(mockMvc.perform(get("/api/demo/chain").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(chain.size()).isEqualTo(DemoCatalog.CHAIN.size());

        for (JsonNode stage : chain) {
            String id = stage.path("id").asText();
            JsonNode run = postJson("/api/demo/scenarios/" + id + "/run");
            assertThat(run.path("eventsGenerated").asInt()).as(id + " events").isPositive();
            assertThat(run.path("alertsCreated").asInt()).as(id + " alerts").isPositive();
            assertThat(run.path("incidentIds").size()).as(id + " incidents").isPositive();
            assertThat(run.path("rulesFired").toString()).as(id + " rule").contains(stage.path("expectedRule").asText());
        }

        // Re-timed to "now": the newest generated event is within a minute of the current time.
        Instant newest = securityEventRepository.findAll().stream().map(SecurityEvent::getEventTimestamp)
                .max(Comparator.naturalOrder()).orElseThrow();
        assertThat(Duration.between(newest, Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void seedSpreadsBenignBaselineOverLast24Hours() throws Exception {
        JsonNode seed = postJson("/api/demo/seed");
        assertThat(seed.path("eventsGenerated").asInt()).isGreaterThanOrEqualTo(200);
        assertThat(seed.path("incidentIds").size()).isZero();

        List<Instant> times = securityEventRepository.findAll().stream().map(SecurityEvent::getEventTimestamp).toList();
        Instant now = Instant.now();
        assertThat(times).allSatisfy(t -> assertThat(t).isBetween(now.minus(Duration.ofHours(25)), now.plusSeconds(60)));
        assertThat(times.stream().min(Comparator.naturalOrder()).orElseThrow()).isBefore(now.minus(Duration.ofHours(20)));
    }

    @Test
    void resetRemovesOnlySimulatorData() throws Exception {
        postJson("/api/demo/scenarios/brute_force/run");
        ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(Map.of(
                "eventType", "OTHER", "severity", "LOW", "username", "real-user")), "real-event-1");
        assertThat(incidentRepository.count()).isPositive();

        JsonNode reset = postJson("/api/demo/reset");
        assertThat(reset.path("incidents").asInt()).isPositive();
        assertThat(reset.path("events").asInt()).isPositive();
        assertThat(incidentRepository.count()).isZero();
        assertThat(alertRepository.findAll()).allSatisfy(a -> assertThat(a.getRunId()).isNull());
        assertThat(securityEventRepository.findAll()).extracting(SecurityEvent::getUsername).containsExactly("real-user");
    }

    @Test
    void replayStreamsSampleThroughParsersAndResetRemovesIt() throws Exception {
        JsonNode replay = postJson("/api/demo/replay/auth_log");
        assertThat(replay.path("report").path("accepted").asInt()).isEqualTo(32);
        assertThat(replay.path("report").path("parseErrors").asInt()).isEqualTo(1);
        assertThat(replay.path("rulesFired").toString()).contains("BRUTE_FORCE").contains("CREDENTIAL_STUFFING");
        assertThat(replay.path("incidentIds").size()).isPositive();
        Instant newest = securityEventRepository.findAll().stream().map(SecurityEvent::getEventTimestamp)
                .max(Comparator.naturalOrder()).orElseThrow();
        assertThat(Duration.between(newest, Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));

        mockMvc.perform(post("/api/demo/replay/nope").header("Authorization", admin)).andExpect(status().isNotFound());

        postJson("/api/demo/reset");
        assertThat(securityEventRepository.count()).isZero();
        assertThat(incidentRepository.count()).isZero();
    }

    @Test
    void unknownScenarioIs404AndNonAdminIsForbidden() throws Exception {
        mockMvc.perform(post("/api/demo/scenarios/no_such_thing/run").header("Authorization", admin))
                .andExpect(status().isNotFound());
        String analyst = bearerFor("demo-analyst", Role.ANALYST);
        mockMvc.perform(post("/api/demo/scenarios/brute_force/run").header("Authorization", analyst))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/demo/reset").header("Authorization", analyst))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/demo/scenarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicDemoInfoListsQuickLoginsWhenEnabled() throws Exception {
        mockMvc.perform(get("/api/public/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.demoMode").value(true))
                .andExpect(jsonPath("$.data.quickLogins.length()").value(3))
                .andExpect(jsonPath("$.data.quickLogins[0].role").value("ADMIN"));
    }
}
