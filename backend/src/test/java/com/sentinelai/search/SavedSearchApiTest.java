package com.sentinelai.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Saved searches: validation, per-user isolation, pinned widget count + 24h hourly trend. */
class SavedSearchApiTest extends IntegrationTestSupport {

    @Autowired private IngestionService ingestionService;

    @Test
    void saveValidatePinAndWidgetStats() throws Exception {
        String alice = bearerFor("ss-alice", Role.VIEWER);
        String bob = bearerFor("ss-bob", Role.ANALYST);
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("sourceIp", "10.60.0.1");
            p.put("username", "ss-user");
            p.put("eventTimestamp", now.minusSeconds(60 + i).toString());
            ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
        }
        String body = "{\"name\":\"Failed from 10.60\",\"query\":\"ip = '10.60.0.1' AND outcome = 'FAILURE'\","
                + "\"filters\":{\"range\":\"24h\"},\"pinned\":true}";
        String res = mockMvc.perform(post("/api/saved-searches").header("Authorization", alice)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(res).path("data").path("id").asLong();

        mockMvc.perform(post("/api/saved-searches").header("Authorization", alice).contentType("application/json").content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/saved-searches").header("Authorization", alice).contentType("application/json")
                        .content("{\"name\":\"bad\",\"query\":\"color = 'red'\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/saved-searches").header("Authorization", alice).contentType("application/json")
                        .content("{\"name\":\"bad2\",\"filters\":{\"range\":\"forever\"}}"))
                .andExpect(status().isBadRequest());

        String pinned = mockMvc.perform(get("/api/saved-searches/pinned").header("Authorization", alice))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode w = objectMapper.readTree(pinned).path("data").get(0);
        assertThat(w.path("count").asLong()).isEqualTo(3);
        assertThat(w.path("hourly").size()).isEqualTo(24);
        long trendTotal = 0;
        for (JsonNode h : w.path("hourly")) {
            trendTotal += h.asLong();
        }
        assertThat(trendTotal).isEqualTo(3);

        // Another user can't see or delete it.
        mockMvc.perform(get("/api/saved-searches").header("Authorization", bob)).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(delete("/api/saved-searches/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/saved-searches/" + id).header("Authorization", alice)).andExpect(status().isOk());
    }
}
