package com.sentinelai.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Query + filters + pagination + CSV export over HTTP, and that input stays data, never SQL. */
class EventSearchApiTest extends IntegrationTestSupport {

    @Autowired private IngestionService ingestionService;

    private String viewer;

    @BeforeEach
    void setUp() throws Exception {
        viewer = bearerFor("search-viewer", Role.VIEWER);
        event("FAILED_LOGIN", "10.0.0.5", "alice", "/login", "2026-08-01T10:00:00Z", null);
        event("FAILED_LOGIN", "10.0.0.5", "bob", "/login", "2026-08-01T10:05:00Z", null);
        event("LOGIN_SUCCESS", "10.0.0.5", "alice", "/login", "2026-08-01T10:06:00Z", null);
        event("OTHER", "10.0.0.9", "carol", "/admin/users", "2026-08-02T09:00:00Z", null);
        event("OTHER", "10.0.0.9", "=cmd|calc", "/x", "2026-08-02T09:30:00Z", "SUCCESS");
    }

    private void event(String type, String ip, String user, String resource, String ts, String outcome) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("eventType", type);
        p.put("severity", "LOW");
        p.put("sourceIp", ip);
        p.put("username", user);
        p.put("resource", resource);
        p.put("eventTimestamp", ts);
        if (outcome != null) {
            p.put("outcome", outcome);
        }
        ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
    }

    private JsonNode search(String params) throws Exception {
        String res = mockMvc.perform(get("/api/search/events" + params).header("Authorization", viewer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).path("data");
    }

    private JsonNode searchQ(String query) throws Exception {
        String res = mockMvc.perform(get("/api/search/events").param("q", query).header("Authorization", viewer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).path("data");
    }

    @Test
    void queryLanguage() throws Exception {
        assertThat(searchQ("sourceIp = '10.0.0.5' AND outcome = 'FAILURE'").path("totalElements").asInt()).isEqualTo(2);
        assertThat(searchQ("(user = 'alice' OR user = 'carol') AND NOT type = 'LOGIN_SUCCESS'").path("totalElements").asInt())
                .isEqualTo(2);
        assertThat(searchQ("resource LIKE '/admin*'").path("totalElements").asInt()).isEqualTo(1);
        assertThat(searchQ("user IN ('alice', 'bob')").path("totalElements").asInt()).isEqualTo(3);
        assertThat(searchQ("time >= '2026-08-02T00:00:00Z'").path("totalElements").asInt()).isEqualTo(2);
        // Newest first.
        assertThat(search("").path("content").get(0).path("username").asText()).isEqualTo("=cmd|calc");
    }

    @Test
    void filtersAndPagination() throws Exception {
        assertThat(search("?ip=10.0.0.9").path("totalElements").asInt()).isEqualTo(2);
        assertThat(search("?outcome=SUCCESS&user=alice").path("totalElements").asInt()).isEqualTo(1);
        assertThat(search("?from=2026-08-01T10:04:00Z&to=2026-08-01T23:00:00Z").path("totalElements").asInt()).isEqualTo(2);
        JsonNode page = search("?size=2&page=1");
        assertThat(page.path("content").size()).isEqualTo(2);
        assertThat(page.path("totalElements").asInt()).isEqualTo(5);
    }

    @Test
    void injectionStaysDataAndErrorsAre400() throws Exception {
        assertThat(searchQ("user = 'x'' OR 1=1 --'").path("totalElements").asInt()).isZero();
        mockMvc.perform(get("/api/search/events").param("q", "user = ").header("Authorization", viewer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("Expected a value")));
        mockMvc.perform(get("/api/search/events?from=2026-09-01T00:00:00Z&to=2026-08-01T00:00:00Z")
                        .header("Authorization", viewer))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/search/events")).andExpect(status().isUnauthorized());
    }

    @Test
    void translateAndTopN() throws Exception {
        mockMvc.perform(get("/api/search/events/translate").param("text", "failed logins from 10.0.0.5").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.query").value("type = 'FAILED_LOGIN' AND ip = '10.0.0.5'"));
        mockMvc.perform(get("/api/search/events/top").param("field", "username").param("q", "ip = '10.0.0.5'")
                        .header("Authorization", viewer))
                .andExpect(jsonPath("$.data[0].value").value("alice"))
                .andExpect(jsonPath("$.data[0].count").value(2));
        mockMvc.perform(get("/api/search/events/top").param("field", "rawPayload").header("Authorization", viewer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void csvExportIsFormulaSafe() throws Exception {
        String csv = mockMvc.perform(get("/api/search/events/export").param("q", "ip = '10.0.0.9'").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv.lines().toList()).hasSize(3);
        assertThat(csv).startsWith("id,time,eventType").contains("'=cmd|calc").doesNotContain(",=cmd");
    }
}
