package com.sentinelai.incident.repository;

import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentEvent;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.domain.IncidentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the incident ↔ security_event many-to-many link via the {@link IncidentEvent}
 * join entity (composite key + {@code added_at}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class IncidentEventLinkTest {

    @Autowired
    private IncidentRepository incidentRepository;
    @Autowired
    private SecurityEventRepository securityEventRepository;
    @Autowired
    private IncidentEventRepository incidentEventRepository;
    @Autowired
    private OrganizationRepository organizationRepository;

    private Organization org;

    @BeforeEach
    void setUp() {
        org = organizationRepository.findById(1L).orElseThrow();
    }

    private SecurityEvent newEvent(EventType type) {
        return securityEventRepository.save(SecurityEvent.builder()
                .org(org)
                .eventType(type)
                .severity(Severity.MEDIUM)
                .sourceIp("203.0.113.10")
                .username("jdoe")
                .eventTimestamp(Instant.now())
                .rawPayload("{\"reason\":\"test\"}")
                .build());
    }

    @Test
    void linksMultipleEventsToAnIncident() {
        Incident incident = incidentRepository.save(Incident.builder()
                .org(org)
                .title("Brute force against jdoe")
                .status(IncidentStatus.OPEN)
                .severity(Severity.HIGH)
                .feedback(IncidentFeedback.UNREVIEWED)
                .build());

        SecurityEvent e1 = newEvent(EventType.FAILED_LOGIN);
        SecurityEvent e2 = newEvent(EventType.BRUTE_FORCE);

        incidentEventRepository.save(new IncidentEvent(incident, e1));
        incidentEventRepository.save(new IncidentEvent(incident, e2));
        incidentEventRepository.flush();

        assertThat(incidentEventRepository.countById_IncidentId(incident.getId())).isEqualTo(2);
        assertThat(incidentEventRepository.findById_IncidentId(incident.getId()))
                .hasSize(2)
                .allSatisfy(link -> {
                    assertThat(link.getAddedAt()).isNotNull();
                    assertThat(link.getIncident().getId()).isEqualTo(incident.getId());
                });

        assertThat(incidentEventRepository.findById_EventId(e1.getId())).hasSize(1);
    }
}
