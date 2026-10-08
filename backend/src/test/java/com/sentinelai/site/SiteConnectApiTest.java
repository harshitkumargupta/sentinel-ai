package com.sentinelai.site;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.util.Hashing;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Connect My Website: key issued once and stored hashed, attestation required, test connection, web classification. */
class SiteConnectApiTest extends IntegrationTestSupport {

    @Autowired private JdbcTemplate jdbc;

    private JsonNode data(String json) throws Exception {
        return objectMapper.readTree(json).path("data");
    }

    @Test
    void connectIssuesHashedKeyAndReportsFirstEvents() throws Exception {
        String admin = bearerFor("conn-admin", Role.ADMIN);
        String analyst = bearerFor("conn-analyst", Role.ANALYST);
        String body = "{\"name\":\"My Shop\",\"url\":\"https://shop.example.test\",\"authorized\":%s,\"method\":\"MIDDLEWARE\"}";

        mockMvc.perform(post("/api/sites/connect").header("Authorization", admin).contentType("application/json")
                .content(body.formatted("false"))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/sites/connect").header("Authorization", admin).contentType("application/json")
                .content(body.formatted("true").replace("https://shop.example.test", "ftp://x"))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/sites/connect").header("Authorization", analyst).contentType("application/json")
                .content(body.formatted("true"))).andExpect(status().isForbidden());

        JsonNode created = data(mockMvc.perform(post("/api/sites/connect").header("Authorization", admin)
                .contentType("application/json").content(body.formatted("true")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        long siteId = created.path("site").path("id").asLong();
        String key = created.path("apiKey").path("apiKey").asText();
        assertThat(key).startsWith("sk_").hasSizeGreaterThan(30);
        String stored = jdbc.queryForObject("select key_hash from api_keys where id = ?", String.class,
                created.path("apiKey").path("keyId").asLong());
        assertThat(stored).isEqualTo(Hashing.sha256Hex(key)).doesNotContain(key);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'SITE_CONNECT' and entity_id = ?",
                Long.class, siteId)).isEqualTo(1);
        // listing never returns the key again
        assertThat(mockMvc.perform(get("/api/sites").header("Authorization", admin)).andReturn().getResponse()
                .getContentAsString()).doesNotContain(key);

        JsonNode before = data(mockMvc.perform(get("/api/sites/" + siteId + "/connection").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(before.path("connected").asBoolean()).isFalse();

        // what the middleware snippet sends: plain request events
        List<String> events = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            events.add("{\"sourceIp\":\"198.51.100.7\",\"method\":\"POST\",\"path\":\"/login\",\"status\":401,\"username\":\"admin\",\"userAgent\":\"curl/8\"}");
        }
        events.add("{\"sourceIp\":\"198.51.100.7\",\"method\":\"GET\",\"path\":\"/.env\",\"status\":404}");
        events.add("{\"sourceIp\":\"198.51.100.7\",\"method\":\"GET\",\"path\":\"/products?id=1' OR '1'='1\",\"status\":500}");
        events.add("{\"sourceIp\":\"198.51.100.7\",\"method\":\"GET\",\"path\":\"/products?id=2\",\"status\":200}");
        mockMvc.perform(post("/api/ingest/events").header("X-API-Key", key).contentType("application/json")
                .content("[" + String.join(",", events) + "]")).andExpect(status().isOk());

        // sites are per-user scoped: an analyst without access to this site doesn't see it
        mockMvc.perform(get("/api/sites/" + siteId + "/connection").header("Authorization", analyst)).andExpect(status().isNotFound());
        JsonNode after = data(mockMvc.perform(get("/api/sites/" + siteId + "/connection").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(after.path("connected").asBoolean()).isTrue();
        assertThat(after.path("eventCount").asInt()).isEqualTo(6);
        assertThat(after.path("lastEventAt").isNull()).isFalse();
        assertThat(jdbc.queryForList("select event_type from security_events where site_id = ? order by id", String.class, siteId))
                .containsExactly("FAILED_LOGIN", "FAILED_LOGIN", "FAILED_LOGIN", "ABNORMAL_ACCESS", "SQL_INJECTION", "OTHER");

        mockMvc.perform(get("/api/sites/999999/connection").header("Authorization", admin)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/ingest/events").header("X-API-Key", "sk_wrong").contentType("application/json")
                .content(events.get(0))).andExpect(status().isUnauthorized());
    }

    @Test
    void monitorCheckRecordsUnreachableSite() throws Exception {
        String admin = bearerFor("mon-admin", Role.ADMIN);
        long siteId = data(mockMvc.perform(post("/api/sites/connect").header("Authorization", admin).contentType("application/json")
                .content("{\"name\":\"Down\",\"url\":\"http://127.0.0.1:9/\",\"authorized\":true,\"method\":\"MONITOR\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("site").path("id").asLong();
        JsonNode r = data(mockMvc.perform(post("/api/sites/" + siteId + "/monitor-check").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(r.path("reachable").asBoolean()).isFalse();
        assertThat(r.path("eventId").asLong()).isPositive();
        String viewer = bearerFor("mon-viewer", Role.VIEWER);
        mockMvc.perform(post("/api/sites/" + siteId + "/monitor-check").header("Authorization", viewer)).andExpect(status().isForbidden());
    }
}
