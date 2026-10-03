package com.sentinelai.incident.repository;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentEvent;
import com.sentinelai.incident.domain.IncidentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the incident ↔ security_event many-to-many link via the {@link IncidentEvent}
 * join entity (composite key + extra {@code added_at} attribute).
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

    private SecurityEvent newEvent(EventType type) {
        return securityEventRepository.save(SecurityEvent.builder()
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
                .title("Brute force against jdoe")
                .status(IncidentStatus.OPEN)
                .severity(Severity.HIGH)
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

        // The same event can be reached from the event side of the link.
        assertThat(incidentEventRepository.findById_EventId(e1.getId())).hasSize(1);
    }
}
