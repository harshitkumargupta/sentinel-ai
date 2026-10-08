package com.sentinelai.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.notification.channel.IncidentNotificationEvent;
import com.sentinelai.notification.channel.NotificationChannelRepository;
import com.sentinelai.notification.channel.NotificationDelivery;
import com.sentinelai.notification.channel.NotificationDeliveryRepository;
import com.sentinelai.notification.channel.NotificationDispatcher;
import com.sentinelai.notification.channel.NotificationRuleRepository;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Channels (in-app / mock email / failing webhook), rules, retries, bell, and non-blocking delivery. */
class NotificationApiTest extends IntegrationTestSupport {

    @Autowired private NotificationDispatcher dispatcher;
    @Autowired private NotificationDeliveryRepository deliveries;
    @Autowired private NotificationChannelRepository channels;
    @Autowired private NotificationRuleRepository rules;
    @Autowired private IngestionService ingestionService;
    @Autowired private DetectionRuleRepository ruleRepository;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        deliveries.deleteAll();
        rules.deleteAll();
        channels.findAll().stream().filter(c -> !"In-app (analysts & admins)".equals(c.getName())).forEach(channels::delete);
        admin = bearerFor("notif-admin", Role.ADMIN);
    }

    private JsonNode post(String url, String body, int expected) throws Exception {
        String res = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                        .header("Authorization", admin).contentType("application/json").content(body == null ? "" : body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).path("data");
    }

    private long channel(String json) throws Exception {
        return post("/api/notification-settings/channels", json, 200).path("id").asLong();
    }

    @Test
    void channelValidationMockEmailAndRetriedWebhookFailure() throws Exception {
        post("/api/notification-settings/channels", "{\"name\":\"x\",\"type\":\"WEBHOOK\",\"target\":\"ftp://h/x\"}", 400);
        post("/api/notification-settings/channels", "{\"name\":\"x\",\"type\":\"WEBHOOK\",\"target\":\"http://169.254.169.254/latest\"}", 400);
        post("/api/notification-settings/channels", "{\"name\":\"x\",\"type\":\"EMAIL\",\"target\":\"not-an-email\"}", 400);

        long email = channel("{\"name\":\"SOC mail\",\"type\":\"EMAIL\",\"target\":\"soc@example.com\"}");
        JsonNode t = post("/api/notification-settings/channels/" + email + "/test", null, 200);
        assertThat(t.path("status").asText()).isEqualTo("MOCKED");
        mockMvc.perform(get("/api/notification-settings/channels").header("Authorization", admin))
                .andExpect(jsonPath("$.data[?(@.name=='SOC mail')].mock").value(org.hamcrest.Matchers.hasItem(true)));

        long hook = channel("{\"name\":\"Dead hook\",\"type\":\"WEBHOOK\",\"target\":\"http://127.0.0.1:9/hook\"}");
        JsonNode w = post("/api/notification-settings/channels/" + hook + "/test", null, 200);
        assertThat(w.path("status").asText()).isEqualTo("FAILED");
        assertThat(w.path("attempts").asInt()).isEqualTo(3);
        assertThat(w.path("lastError").asText()).isNotBlank();

        String viewer = bearerFor("notif-viewer", Role.VIEWER);
        mockMvc.perform(get("/api/notification-settings/channels").header("Authorization", viewer)).andExpect(status().isForbidden());
    }

    @Test
    void rulesRouteIncidentsToChannelsAndTheBell() throws Exception {
        String analyst = bearerFor("notif-analyst", Role.ANALYST);
        long inApp = channels.findAll().stream().filter(c -> "In-app (analysts & admins)".equals(c.getName()))
                .findFirst().orElseThrow().getId();
        long email = channel("{\"name\":\"SOC mail\",\"type\":\"EMAIL\",\"target\":\"soc@example.com\"}");
        post("/api/notification-settings/rules", "{\"name\":\"High brute force\",\"minSeverity\":\"HIGH\",\"ruleType\":\"BRUTE_FORCE\","
                + "\"onIncidentCreated\":true,\"onEscalation\":false,\"channelIds\":[" + inApp + "," + email + "]}", 200);

        List<NotificationDelivery> none = dispatcher.dispatch(new IncidentNotificationEvent(ORG_ID, null, "t", Severity.MEDIUM, 40, true, Set.of("BRUTE_FORCE")));
        assertThat(none).isEmpty(); // below min severity
        assertThat(dispatcher.dispatch(new IncidentNotificationEvent(ORG_ID, null, "t", Severity.HIGH, 70, true, Set.of("PORT_SCAN")))).isEmpty();
        assertThat(dispatcher.dispatch(new IncidentNotificationEvent(ORG_ID, null, "t", Severity.HIGH, 70, false, Set.of("BRUTE_FORCE")))).isEmpty();

        List<NotificationDelivery> sent = dispatcher.dispatch(new IncidentNotificationEvent(ORG_ID, null, "VPN brute force", Severity.CRITICAL, 90, true, Set.of("BRUTE_FORCE")));
        assertThat(sent).extracting(d -> d.getStatus().name()).containsExactlyInAnyOrder("SENT", "MOCKED");

        mockMvc.perform(get("/api/notifications").header("Authorization", analyst))
                .andExpect(jsonPath("$.data[0].message").value(org.hamcrest.Matchers.containsString("VPN brute force")))
                .andExpect(jsonPath("$.data[0].read").value(false));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/notifications/read-all")
                .header("Authorization", analyst)).andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void failingChannelNeverBlocksDetection() throws Exception {
        if (!ruleRepository.existsByName("notif-bf")) {
            ruleRepository.save(DetectionRule.builder().org(organizationRepository.findById(ORG_ID).orElseThrow())
                    .name("notif-bf").ruleType("BRUTE_FORCE").config("{\"threshold\":3,\"windowSeconds\":3600}")
                    .enabled(true).severity(Severity.CRITICAL).mitreTechnique("T1110").version(1).build());
        }
        long hook = channel("{\"name\":\"Dead hook\",\"type\":\"WEBHOOK\",\"target\":\"http://127.0.0.1:9/hook\"}");
        post("/api/notification-settings/rules", "{\"name\":\"All\",\"minSeverity\":\"LOW\",\"onIncidentCreated\":true,"
                + "\"onEscalation\":true,\"channelIds\":[" + hook + "]}", 200);
        for (int i = 0; i < 4; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("username", "notif-victim");
            p.put("eventTimestamp", Instant.parse("2026-10-04T00:00:00Z").plusSeconds(i).toString());
            ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
        }
        assertThat(incidentRepository.findAll()).anyMatch(i -> "user:notif-victim".equals(i.getCorrelationKey()));
        long deadline = System.currentTimeMillis() + 10_000;
        while (deliveries.findAll().stream().noneMatch(d -> d.getStatus() == NotificationDelivery.Status.FAILED)
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(deliveries.findAll()).anyMatch(d -> d.getStatus() == NotificationDelivery.Status.FAILED && d.getAttempts() == 3);
    }
}
