package com.sentinelai.incident;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.exception.InvalidStateTransitionException;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.repository.IncidentTimelineRepository;
import com.sentinelai.incident.service.IncidentService;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentTransitionTest extends IntegrationTestSupport {

    @Autowired private IncidentService incidentService;
    @Autowired private IncidentTimelineRepository timelineRepository;

    private Long incident() {
        return incidentRepository.save(Incident.builder()
                .org(organizationRepository.findById(ORG_ID).orElseThrow())
                .title("t").status(IncidentStatus.OPEN).severity(Severity.HIGH)
                .feedback(IncidentFeedback.UNREVIEWED).build()).getId();
    }

    @Test
    void validTransitionIsRecordedInTimeline() {
        AppUserPrincipal actor = new AppUserPrincipal(createUser("tadmin", Role.ADMIN));
        Long id = incident();

        incidentService.updateStatus(id, IncidentStatus.INVESTIGATING, actor);

        assertThat(incidentRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.INVESTIGATING);
        assertThat(timelineRepository.findByIncidentIdOrderByIdAsc(id))
                .anyMatch(t -> t.getType().equals("STATUS_CHANGE"));
    }

    @Test
    void illegalTransitionThrows() {
        AppUserPrincipal actor = new AppUserPrincipal(createUser("tadmin2", Role.ADMIN));
        Long id = incident();
        // OPEN -> RESOLVED is not allowed (must go through INVESTIGATING -> CONTAINED).
        assertThatThrownBy(() -> incidentService.updateStatus(id, IncidentStatus.RESOLVED, actor))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
