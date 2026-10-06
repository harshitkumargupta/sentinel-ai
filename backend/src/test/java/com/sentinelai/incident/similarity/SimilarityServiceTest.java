package com.sentinelai.incident.similarity;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.cache.IncidentsChangedEvent;
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
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Cosine similarity ranking over a seeded fixture, plus cache invalidation on incident change. */
@SpringBootTest(properties = {"sentinel.ai.enabled=false", "sentinel.kafka.enabled=false"})
@ActiveProfiles("test")
class SimilarityServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Instant BASE = Instant.parse("2026-06-01T03:00:00Z");

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SecurityEventRepository eventRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private IncidentEventRepository incidentEventRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private com.sentinelai.playbook.repository.PlaybookActionRepository playbookActionRepository;
    @Autowired private com.sentinelai.ai.repository.AiAnalysisRepository aiAnalysisRepository;
    @Autowired private SimilarityService similarityService;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private PasswordEncoder passwordEncoder;

    private AppUserPrincipal actor;
    private Incident a;

    @BeforeEach
    void seed() {
        playbookActionRepository.deleteAll();
        aiAnalysisRepository.deleteAll();
        notificationRepository.deleteAll();
        incidentRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        Organization org = organizationRepository.findById(ORG_ID).orElseThrow();
        User u = userRepository.save(User.builder().org(org).username("sim-analyst")
                .email("sim@sentinel.ai").passwordHash(passwordEncoder.encode("x"))
                .role(Role.ANALYST).enabled(true).build());
        actor = new AppUserPrincipal(u);

        a = incident("user:alice", EventType.FAILED_LOGIN, Severity.HIGH);   // query target
        incident("user:bob", EventType.FAILED_LOGIN, Severity.HIGH);          // similar to A
        incident("ip:1.2.3.4", EventType.API_ABUSE, Severity.LOW);           // dissimilar
        events.publishEvent(new IncidentsChangedEvent(ORG_ID)); // clear any cache from prior tests
    }

    private Incident incident(String key, EventType type, Severity sev) {
        Organization org = organizationRepository.getReferenceById(ORG_ID);
        Incident inc = incidentRepository.save(Incident.builder()
                .org(org).title("inc " + key).status(IncidentStatus.OPEN).severity(sev)
                .feedback(IncidentFeedback.UNREVIEWED).correlationKey(key).riskScore(50).build());
        for (int i = 0; i < 3; i++) {
            SecurityEvent e = eventRepository.save(SecurityEvent.builder()
                    .org(org).eventType(type).severity(sev).entityKey(key).honeytoken(false)
                    .eventTimestamp(BASE.plusSeconds(i * 10L)).build());
            incidentEventRepository.save(new IncidentEvent(inc, e));
        }
        return inc;
    }

    @Test
    void ranksTheMostSimilarIncidentFirst() {
        List<SimilarIncident> results = similarityService.findSimilar(a.getId(), actor, 5);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).title()).contains("user:bob");
        assertThat(results.get(0).score()).isGreaterThan(results.get(results.size() - 1).score());
        assertThat(results.get(0).sharedFeatures()).anyMatch(f -> f.startsWith("event:FAILED_LOGIN"));
    }

    @Test
    void cacheInvalidatesWhenIncidentsChange() {
        int before = similarityService.findSimilar(a.getId(), actor, 10).size();
        incident("user:carol", EventType.FAILED_LOGIN, Severity.HIGH); // another similar one
        events.publishEvent(new IncidentsChangedEvent(ORG_ID));         // evict cache
        int after = similarityService.findSimilar(a.getId(), actor, 10).size();
        assertThat(after).isEqualTo(before + 1);
    }
}
