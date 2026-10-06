package com.sentinelai.playbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ai.repository.AiAnalysisRepository;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.domain.User;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.common.repository.OrganizationRepository;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.repository.DetectionRuleRepository;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.repository.IncidentRepository;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.notification.repository.NotificationRepository;
import com.sentinelai.playbook.adapter.MockFirewall;
import com.sentinelai.playbook.adapter.MockIdentity;
import com.sentinelai.playbook.domain.PlaybookAction;
import com.sentinelai.playbook.domain.PlaybookActionStatus;
import com.sentinelai.playbook.repository.PlaybookActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared seeding for playbook tests (subclasses declare their own @SpringBootTest). */
abstract class PlaybookTestSupport {

    protected static final Long ORG_ID = 1L;
    protected static final Instant BASE = Instant.parse("2026-04-01T00:00:00Z");

    @Autowired protected OrganizationRepository organizationRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected DetectionRuleRepository ruleRepository;
    @Autowired protected IngestionService ingestionService;
    @Autowired protected IncidentRepository incidentRepository;
    @Autowired protected AlertRepository alertRepository;
    @Autowired protected AiAnalysisRepository aiAnalysisRepository;
    @Autowired protected PlaybookActionRepository playbookActionRepository;
    @Autowired protected NotificationRepository notificationRepository;
    @Autowired protected com.sentinelai.event.repository.SecurityEventRepository securityEventRepository;
    @Autowired protected PlaybookService playbookService;
    @Autowired protected MockFirewall firewall;
    @Autowired protected MockIdentity identity;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected Clock clock;

    protected User analyst;
    protected User admin;
    protected Incident incident;

    @BeforeEach
    void seed() {
        playbookActionRepository.deleteAll();
        aiAnalysisRepository.deleteAll();
        notificationRepository.deleteAll();
        incidentRepository.deleteAll();
        alertRepository.deleteAll();
        securityEventRepository.deleteAll();
        ruleRepository.deleteAll();
        userRepository.deleteAll();

        Organization org = organizationRepository.findById(ORG_ID).orElseThrow();
        ruleRepository.save(DetectionRule.builder().org(org).name("bf").ruleType("BRUTE_FORCE")
                .config("{\"threshold\":3,\"windowSeconds\":3600,\"groupBy\":\"username\"}")
                .enabled(true).severity(Severity.HIGH).mitreTechnique("T1110").version(1).build());
        analyst = userRepository.save(User.builder().org(org).username("pb-analyst")
                .email("pb-analyst@sentinel.ai").passwordHash(passwordEncoder.encode("x"))
                .role(Role.ANALYST).enabled(true).build());
        admin = userRepository.save(User.builder().org(org).username("pb-admin")
                .email("pb-admin@sentinel.ai").passwordHash(passwordEncoder.encode("x"))
                .role(Role.ADMIN).enabled(true).build());
        incident = bruteForceIncident("mallory");
    }

    protected AppUserPrincipal principal(User u) {
        return new AppUserPrincipal(u);
    }

    protected Incident bruteForceIncident(String user) {
        for (int i = 0; i < 4; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("eventType", "FAILED_LOGIN");
            p.put("severity", "LOW");
            p.put("username", user);
            p.put("sourceIp", "203.0.113.9");
            p.put("eventTimestamp", BASE.plusSeconds(i * 10L).toString());
            ingestionService.ingest(ORG_ID, null, "generic", objectMapper.valueToTree(p), null);
        }
        return incidentRepository.findAll().stream()
                .filter(i -> ("user:" + user).equals(i.getCorrelationKey()))
                .findFirst().orElseThrow();
    }

    protected PlaybookAction propose(String type, String target, Severity risk, User proposer) {
        return playbookActionRepository.save(PlaybookAction.builder()
                .incident(incident)
                .proposedBy(proposer)
                .actionType(type)
                .targetRef(target)
                .reason("test")
                .riskLevel(risk)
                .status(PlaybookActionStatus.PROPOSED)
                .expiresAt(Instant.now(clock).plus(30, ChronoUnit.MINUTES))
                .build());
    }

    protected PlaybookAction reload(Long id) {
        return playbookActionRepository.findById(id).orElseThrow();
    }
}
