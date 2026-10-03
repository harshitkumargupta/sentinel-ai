package com.sentinelai.event.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips a JSON column through MySQL. MySQL normalizes JSON (whitespace/key order is not
 * preserved), so the assertion parses the reloaded value rather than comparing raw strings.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SecurityEventJsonTest {

    @Autowired
    private SecurityEventRepository securityEventRepository;
    @Autowired
    private OrganizationRepository organizationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jsonPayloadRoundTrips() throws Exception {
        Organization org = organizationRepository.findById(1L).orElseThrow();

        String payload = "{\"reason\":\"BAD_PASSWORD\",\"attempts\":5,\"tags\":[\"edge\",\"asn-1234\"]}";

        SecurityEvent saved = securityEventRepository.saveAndFlush(SecurityEvent.builder()
                .org(org)
                .eventType(EventType.FAILED_LOGIN)
                .severity(Severity.LOW)
                .sourceIp("203.0.113.5")
                .username("jdoe")
                .honeytoken(false)
                .eventTimestamp(Instant.now())
                .rawPayload(payload)
                .build());

        // Clear the persistence context so the value is re-read from the database.
        securityEventRepository.flush();
        SecurityEvent reloaded = securityEventRepository.findById(saved.getId()).orElseThrow();

        JsonNode json = objectMapper.readTree(reloaded.getRawPayload());
        assertThat(json.get("reason").asText()).isEqualTo("BAD_PASSWORD");
        assertThat(json.get("attempts").asInt()).isEqualTo(5);
        assertThat(json.get("tags")).hasSize(2);
        assertThat(json.get("tags").get(0).asText()).isEqualTo("edge");
    }
}
