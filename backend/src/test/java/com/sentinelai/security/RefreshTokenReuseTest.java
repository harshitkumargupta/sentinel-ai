package com.sentinelai.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh-token rotation with reuse detection: a rotated (already-used) refresh token presented a
 * second time is rejected AND revokes the whole family, so the attacker's freshly minted token is
 * also dead. Mitigates stolen-refresh-token replay.
 */
class RefreshTokenReuseTest extends IntegrationTestSupport {

    private String refreshBody(String token) throws Exception {
        return "{\"refreshToken\":\"" + token + "\"}";
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheFamily() throws Exception {
        createUser("dave", Role.ANALYST);

        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginBody("dave", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        String firstRefresh = json(login).path("data").path("refreshToken").asText();

        // Rotate once: the first refresh is now revoked, a second (new) refresh is issued.
        MvcResult rotated = mockMvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content(refreshBody(firstRefresh)))
                .andExpect(status().isOk()).andReturn();
        String secondRefresh = json(rotated).path("data").path("refreshToken").asText();

        // Reuse the FIRST (already-rotated) token -> 401 and family revocation.
        mockMvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content(refreshBody(firstRefresh)))
                .andExpect(status().isUnauthorized());

        // Because the family was revoked, even the legitimately rotated second token no longer works.
        mockMvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content(refreshBody(secondRefresh)))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
