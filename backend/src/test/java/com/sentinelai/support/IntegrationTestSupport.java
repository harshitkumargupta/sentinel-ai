package com.sentinelai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.RefreshTokenRepository;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base for full-stack MockMvc tests: real security filter chain + MySQL test DB. Resets the
 * mutable tables before each test and provides helpers to seed users and obtain JWTs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    protected static final Long ORG_ID = 1L;
    protected static final String PASSWORD = "Password@123";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected OrganizationRepository organizationRepository;
    @Autowired
    protected IncidentRepository incidentRepository;
    @Autowired
    protected SecurityEventRepository securityEventRepository;
    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;
    @Autowired
    protected NotificationRepository notificationRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetState() {
        refreshTokenRepository.deleteAll();
        notificationRepository.deleteAll(); // FK RESTRICT -> clear before incidents
        incidentRepository.deleteAll();     // cascades incident_alerts / incident_events / timeline
        securityEventRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected User createUser(String username, Role role) {
        return userRepository.save(User.builder()
                .org(organizationRepository.findById(ORG_ID).orElseThrow())
                .username(username)
                .email(username + "@sentinel.ai")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(role)
                .enabled(true)
                .build());
    }

    protected String loginAndGetToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new LoginBody(username, PASSWORD))))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }

    protected String bearerFor(String username, Role role) throws Exception {
        createUser(username, role);
        return "Bearer " + loginAndGetToken(username);
    }

    public record LoginBody(String username, String password) {
    }
}
