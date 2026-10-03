package com.sentinelai.auth;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends IntegrationTestSupport {

    private String loginJson(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginBody(username, password));
    }

    @Test
    void loginSucceedsAndReturnsTokens() throws Exception {
        createUser("alice", Role.ADMIN);

        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(loginJson("alice", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        createUser("bob", Role.ANALYST);

        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(loginJson("bob", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void accountLocksAfterFiveFailures() throws Exception {
        createUser("carol", Role.VIEWER);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login").contentType("application/json")
                            .content(loginJson("carol", "wrong")))
                    .andExpect(status().isUnauthorized());
        }

        User locked = userRepository.findByUsername("carol").orElseThrow();
        assertThat(locked.getFailedLoginAttempts()).isGreaterThanOrEqualTo(5);
        assertThat(locked.getLockedUntil()).isNotNull();

        // Even the correct password is now rejected while locked.
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(loginJson("carol", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void meReturnsCurrentUserWithToken() throws Exception {
        String bearer = bearerFor("dave", Role.ANALYST);

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("dave"))
                .andExpect(jsonPath("$.data.role").value("ANALYST"));
    }
}
