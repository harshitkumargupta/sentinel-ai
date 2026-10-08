package com.sentinelai.logsource;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.event.domain.EventOutcome;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Raw-line ingest (agent) and file upload: parsing by source type, outcomes, error counting, auth. */
class RawIngestApiTest extends IntegrationTestSupport {

    private static final String FAILED = "Oct  8 10:00:01 web-01 sshd[1]: Failed password for alice from 203.0.113.5 port 22 ssh2";
    private static final String ACCEPTED = "Oct  8 10:00:09 web-01 sshd[2]: Accepted password for alice from 203.0.113.5 port 22 ssh2";

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = bearerFor("raw-admin", Role.ADMIN);
    }

    private JsonNode createSource(String type) throws Exception {
        String body = mockMvc.perform(post("/api/log-sources").header("Authorization", admin)
                        .contentType("application/json").content("{\"name\":\"raw " + type + "\",\"type\":\"" + type + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    @Test
    void agentLinesUseTheSourceTypeParserAndCountErrors() throws Exception {
        JsonNode src = createSource("AUTH");
        String key = src.path("apiKey").path("apiKey").asText();
        String body = objectMapper.writeValueAsString(java.util.Map.of("lines", java.util.List.of(
                FAILED, ACCEPTED, "Oct  8 10:00:10 web-01 CRON[3]: session opened for user root", "not syslog")));

        String res = mockMvc.perform(post("/api/ingest/raw").header("X-API-Key", key)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode report = objectMapper.readTree(res).path("data");
        assertThat(report.path("format").asText()).isEqualTo("AUTH_LOG");
        assertThat(report.path("accepted").asInt()).isEqualTo(2);
        assertThat(report.path("skipped").asInt()).isEqualTo(1);
        assertThat(report.path("parseErrors").asInt()).isEqualTo(1);

        assertThat(securityEventRepository.findAll()).extracting(SecurityEvent::getOutcome)
                .containsExactlyInAnyOrder(EventOutcome.FAILURE, EventOutcome.SUCCESS);
        assertThat(securityEventRepository.findAll()).filteredOn(e -> e.getEventType() == EventType.FAILED_LOGIN)
                .singleElement().satisfies(e -> {
                    assertThat(e.getUsername()).isEqualTo("alice");
                    assertThat(e.getRawPayload()).contains("rawMessage");
                    assertThat(e.getEntityKey()).isEqualTo("host:web-01"); // enables Isolate Host
                });
    }

    @Test
    void uploadParsesFileWithExplicitFormat() throws Exception {
        long id = createSource("WEB_SERVER").path("source").path("id").asLong();
        String file = "10.0.0.1 - - [08/Oct/2026:10:15:01 +0000] \"GET /a HTTP/1.1\" 200 10\n"
                + "10.0.0.1 - - [08/Oct/2026:10:15:02 +0000] \"GET /b HTTP/1.1\" 404 10\n"
                + "broken line\n";
        String res = mockMvc.perform(multipart("/api/log-sources/" + id + "/upload")
                        .file(new MockMultipartFile("file", "access.log", "text/plain", file.getBytes(StandardCharsets.UTF_8)))
                        .param("format", "ACCESS_LOG")
                        .header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode report = objectMapper.readTree(res).path("data");
        assertThat(report.path("accepted").asInt()).isEqualTo(2);
        assertThat(report.path("parseErrors").asInt()).isEqualTo(1);
        assertThat(securityEventRepository.findAll()).extracting(SecurityEvent::getOutcome)
                .containsExactlyInAnyOrder(EventOutcome.SUCCESS, EventOutcome.FAILURE);
        assertThat(securityEventRepository.findAll()).allSatisfy(e -> assertThat(e.getClientEventId()).isNull());
    }

    @Test
    void rawIngestRequiresKeyAndValidBody() throws Exception {
        mockMvc.perform(post("/api/ingest/raw").contentType("application/json").content("{\"lines\":[\"x\"]}"))
                .andExpect(status().isUnauthorized());
        String key = createSource("APPLICATION").path("apiKey").path("apiKey").asText();
        mockMvc.perform(post("/api/ingest/raw").header("X-API-Key", key).contentType("application/json")
                        .content("{\"lines\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ingest/raw").header("X-API-Key", key).contentType("application/json")
                        .content("{\"format\":\"NOPE\",\"lines\":[\"x\"]}"))
                .andExpect(status().isBadRequest());
        String viewer = bearerFor("raw-viewer", Role.VIEWER);
        long id = createSource("FIREWALL").path("source").path("id").asLong();
        mockMvc.perform(multipart("/api/log-sources/" + id + "/upload")
                        .file(new MockMultipartFile("file", "f.csv", "text/csv", "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", viewer))
                .andExpect(status().isForbidden());
    }
}
