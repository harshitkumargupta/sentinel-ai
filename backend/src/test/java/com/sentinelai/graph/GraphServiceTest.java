package com.sentinelai.graph;

import com.sentinelai.alert.domain.Alert;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.graph.GraphDtos.GraphResponse;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.domain.IncidentAlert;
import com.sentinelai.incident.domain.IncidentEvent;
import com.sentinelai.incident.domain.IncidentFeedback;
import com.sentinelai.incident.domain.IncidentStatus;
import com.sentinelai.incident.repository.IncidentAlertRepository;
import com.sentinelai.incident.repository.IncidentEventRepository;
import com.sentinelai.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "sentinel.graph.max-nodes=6")
class GraphServiceTest extends IntegrationTestSupport {

    @Autowired private GraphService graphService;
    @Autowired private IncidentEventRepository incidentEventRepository;
    @Autowired private IncidentAlertRepository incidentAlertRepository;
    @Autowired private AlertRepository alertRepository;

    private Organization org() {
        return organizationRepository.findById(ORG_ID).orElseThrow();
    }

    private Incident incident() {
        return incidentRepository.save(Incident.builder().org(org()).title("i")
                .status(IncidentStatus.OPEN).severity(Severity.HIGH).feedback(IncidentFeedback.UNREVIEWED).build());
    }

    private SecurityEvent event(String user, String ip, String country, Instant ts) {
        return securityEventRepository.save(SecurityEvent.builder().org(org())
                .eventType(EventType.SUSPICIOUS_LOGIN).severity(Severity.LOW)
                .username(user).sourceIp(ip).geoCountry(country).honeytoken(false).eventTimestamp(ts).build());
    }

    private AppUserPrincipal actor() {
        return new AppUserPrincipal(createUser("graphadmin", Role.ADMIN));
    }

    @Test
    void aggregatesEdgesAndOrdersKillChain() {
        Incident inc = incident();
        Instant t = Instant.parse("2026-02-01T12:00:00Z");
        var e1 = event("bob", "1.2.3.4", "US", t);
        var e2 = event("bob", "1.2.3.4", "US", t.plusSeconds(30));
        incidentEventRepository.save(new IncidentEvent(inc, e1));
        incidentEventRepository.save(new IncidentEvent(inc, e2));

        var late = alertRepository.save(Alert.builder().org(org()).ruleType("BRUTE_FORCE")
                .severity(Severity.HIGH).mitreTechnique("T1110").message("m").createdAt(t.plusSeconds(60)).build());
        var early = alertRepository.save(Alert.builder().org(org()).ruleType("SUSPICIOUS_LOGIN")
                .severity(Severity.MEDIUM).mitreTechnique("T1078").message("m").createdAt(t).build());
        incidentAlertRepository.save(new IncidentAlert(inc, late));
        incidentAlertRepository.save(new IncidentAlert(inc, early));

        GraphResponse g = graphService.build(inc.getId(), actor());

        assertThat(g.nodes()).anyMatch(n -> n.type().equals("user"));
        assertThat(g.nodes()).anyMatch(n -> n.type().equals("ip"));
        // Two logins from the same user->ip aggregate into one edge with count 2.
        assertThat(g.edges()).anyMatch(e -> e.type().equals("LOGIN_FROM") && e.count() == 2);
        // Kill chain ordered: T1078 (stage 1) before T1110 (stage 3).
        assertThat(g.killChain()).extracting("stage").containsExactly(1, 3);
    }

    @Test
    void capsNodesAndMarksTruncated() {
        Incident inc = incident();
        Instant t = Instant.parse("2026-02-01T12:00:00Z");
        for (int i = 0; i < 10; i++) {
            var e = event("user" + i, "10.0.0." + i, "US", t.plusSeconds(i));
            incidentEventRepository.save(new IncidentEvent(inc, e));
        }
        GraphResponse g = graphService.build(inc.getId(), actor());
        assertThat(g.truncated()).isTrue();
        assertThat(g.nodes().size()).isLessThanOrEqualTo(7); // cap 6 + aggregate node
        assertThat(g.nodes()).anyMatch(n -> n.type().equals("aggregate"));
    }
}
