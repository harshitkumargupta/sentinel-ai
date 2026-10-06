package com.sentinelai.security;

import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Malicious/oversized JSON must be refused cleanly (4xx) rather than crashing the parser or the
 * thread (5xx). Exercises the Jackson StreamReadConstraints (nesting depth + string length).
 */
class PayloadLimitsTest extends IntegrationTestSupport {

    @Test
    void deeplyNestedJsonIsRejectedWith4xxNot5xx() throws Exception {
        StringBuilder sb = new StringBuilder();
        int depth = 5_000; // well beyond the configured maxNestingDepth (64)
        for (int i = 0; i < depth; i++) {
            sb.append("{\"a\":");
        }
        sb.append("1");
        sb.append("}".repeat(depth));

        int status = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(sb.toString()))
                .andReturn().getResponse().getStatus();
        // Must be a client error (malformed/oversized), never a 500.
        org.assertj.core.api.Assertions.assertThat(status).isBetween(400, 499);
    }

    @Test
    void oversizedStringValueIsRejectedWith4xxNot5xx() throws Exception {
        String huge = "x".repeat(2_000_000); // beyond maxStringLength (200k)
        String body = "{\"username\":\"" + huge + "\",\"password\":\"p\"}";
        int status = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(body))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isBetween(400, 499);
    }
}
