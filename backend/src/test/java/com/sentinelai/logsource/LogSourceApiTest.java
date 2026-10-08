package com.sentinelai.logsource;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.common.util.Hashing;
import com.sentinelai.site.repository.ApiKeyRepository;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Log Sources CRUD + key handling, and POST /api/ingest/events auth, shapes, limits and errors. */
class LogSourceApiTest extends IntegrationTestSupport {

    private static final String EVENT = "{\"eventType\":\"FAILED_LOGIN\",\"severity\":\"LOW\",\"username\":\"u1\",\"sourceIp\":\"203.0.113.5\"}";

    @Autowired private ApiKeyRepository apiKeyRepository;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = bearerFor("ls-admin", Role.ADMIN);
    }

    private JsonNode create(String name, String type) throws Exception {
        String body = mockMvc.perform(post("/api/log-sources").header("Authorization", admin)
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"type\":\"" + type + "\",\"description\":\"test\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private JsonNode ingest(String key, String body, int expectedStatus) throws Exception {
        String res = mockMvc.perform(post("/api/ingest/events").header("X-API-Key", key)
                        .contentType("application/json").content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res);
    }

    private JsonNode find(long id) throws Exception {
        String res = mockMvc.perform(get("/api/log-sources").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode s : objectMapper.readTree(res).path("data")) {
            if (s.path("id").asLong() == id) {
                return s;
            }
        }
        return null;
    }

    @Test
    void createIssuesKeyOnceAndStoresOnlyItsHash() throws Exception {
        JsonNode created = create("nginx edge", "WEB_SERVER");
        String raw = created.path("apiKey").path("apiKey").asText();
        assertThat(raw).startsWith("sk_");
        assertThat(created.path("source").path("type").asText()).isEqualTo("WEB_SERVER");
        assertThat(created.path("source").path("health").asText()).isEqualTo("NEVER");
        assertThat(apiKeyRepository.findWithSiteAndOrgByKeyHash(Hashing.sha256Hex(raw))).isPresent();
        assertThat(apiKeyRepository.findAll()).noneMatch(k -> k.getKeyHash().equals(raw));
        // The list never returns key material.
        assertThat(find(created.path("source").path("id").asLong()).toString()).doesNotContain(raw);
    }

    @Test
    void ingestAcceptsAllShapesTagsSourceAndUpdatesStats() throws Exception {
        JsonNode created = create("auth server", "AUTH");
        long id = created.path("source").path("id").asLong();
        String key = created.path("apiKey").path("apiKey").asText();

        assertThat(ingest(key, EVENT, 200).path("data").path("accepted").asInt()).isEqualTo(1);
        assertThat(ingest(key, "[" + EVENT + "," + EVENT + "]", 200).path("data").path("accepted").asInt()).isEqualTo(2);
        JsonNode wrapped = ingest(key, "{\"events\":[{\"payload\":" + EVENT + "}, 42]}", 200).path("data");
        assertThat(wrapped.path("accepted").asInt()).isEqualTo(1);
        assertThat(wrapped.path("rejected").asInt()).isEqualTo(1);
        assertThat(wrapped.path("errors").get(0).path("index").asInt()).isEqualTo(1);

        assertThat(securityEventRepository.findAll()).hasSize(4)
                .allSatisfy(e -> assertThat(e.getSite().getId()).isEqualTo(id));
        JsonNode view = find(id);
        assertThat(view.path("totalEvents").asLong()).isEqualTo(4);
        assertThat(view.path("parseErrors").asLong()).isEqualTo(1);
        assertThat(view.path("health").asText()).isEqualTo("RECEIVING");
        assertThat(view.path("eventsPerSecond").asDouble()).isPositive();
    }

    @Test
    void ingestAuthIsKeyOnly() throws Exception {
        ingest("sk_not_a_real_key", EVENT, 401);
        mockMvc.perform(post("/api/ingest/events").contentType("application/json").content(EVENT))
                .andExpect(status().isUnauthorized());
        String analyst = bearerFor("ls-analyst", Role.ANALYST);
        mockMvc.perform(post("/api/ingest/events").header("Authorization", analyst)
                        .contentType("application/json").content(EVENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledSourceIsRefusedUntilReEnabled() throws Exception {
        JsonNode created = create("fw", "FIREWALL");
        long id = created.path("source").path("id").asLong();
        String key = created.path("apiKey").path("apiKey").asText();

        mockMvc.perform(patch("/api/log-sources/" + id + "/enabled").header("Authorization", admin)
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.health").value("DISABLED"));
        assertThat(ingest(key, EVENT, 403).path("error").path("code").asText()).isEqualTo("LOG_SOURCE_DISABLED");

        mockMvc.perform(patch("/api/log-sources/" + id + "/enabled").header("Authorization", admin)
                        .contentType("application/json").content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        ingest(key, EVENT, 200);
    }

    @Test
    void limitsValidationRbacAndDelete() throws Exception {
        JsonNode created = create("app", "APPLICATION");
        long id = created.path("source").path("id").asLong();
        String key = created.path("apiKey").path("apiKey").asText();

        ingest(key, "[]", 400);
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i < 1001; i++) {
            many.append(i == 0 ? "" : ",").append(EVENT);
        }
        ingest(key, many.append("]").toString(), 400);

        mockMvc.perform(post("/api/log-sources").header("Authorization", admin).contentType("application/json")
                        .content("{\"name\":\"<script>x</script>\",\"type\":\"AUTH\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/log-sources").header("Authorization", admin).contentType("application/json")
                        .content("{\"name\":\"no type\"}"))
                .andExpect(status().isBadRequest());
        String analyst = bearerFor("ls-analyst2", Role.ANALYST);
        mockMvc.perform(post("/api/log-sources").header("Authorization", analyst).contentType("application/json")
                        .content("{\"name\":\"x\",\"type\":\"AUTH\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/log-sources/1").header("Authorization", admin)).andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/log-sources/" + id).header("Authorization", admin)).andExpect(status().isOk());
        ingest(key, EVENT, 401); // keys are removed with the source
    }
}
