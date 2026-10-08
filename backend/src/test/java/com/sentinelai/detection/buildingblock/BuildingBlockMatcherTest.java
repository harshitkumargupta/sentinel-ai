package com.sentinelai.detection.buildingblock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.support.RuleTestFixtures;
import com.sentinelai.event.domain.EventOutcome;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Operators, CIDR/hour handling, reference-set delegation and fail-closed on missing blocks. */
class BuildingBlockMatcherTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final BuildingBlockRepository repo = mock(BuildingBlockRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ReferenceLookup> lookupProvider = mock(ObjectProvider.class);
    private final BuildingBlockMatcher matcher = new BuildingBlockMatcher(repo, mapper, lookupProvider);

    private SecurityEvent event(String ip, String user) {
        return RuleTestFixtures.event(EventType.FAILED_LOGIN).sourceIp(ip).username(user)
                .outcome(EventOutcome.FAILURE).severity(Severity.LOW)
                .eventTimestamp(Instant.parse("2026-10-08T03:15:00Z")).build();
    }

    private boolean test(SecurityEvent e, String field, ConditionOperator op, String... values) {
        return matcher.test(1L, e, new Condition(field, op, List.of(values)));
    }

    @Test
    void operators() {
        SecurityEvent e = event("203.0.113.9", "Root");
        assertThat(test(e, "username", ConditionOperator.EQUALS, "root")).isTrue(); // case-insensitive
        assertThat(test(e, "username", ConditionOperator.NOT_EQUALS, "root")).isFalse();
        assertThat(test(e, "username", ConditionOperator.IN, "admin", "root")).isTrue();
        assertThat(test(e, "username", ConditionOperator.NOT_IN, "admin")).isTrue();
        assertThat(test(e, "eventType", ConditionOperator.EQUALS, "FAILED_LOGIN")).isTrue();
        assertThat(test(e, "outcome", ConditionOperator.EQUALS, "FAILURE")).isTrue();
        assertThat(test(e, "sourceIp", ConditionOperator.STARTS_WITH, "203.0.")).isTrue();
        assertThat(test(e, "sourceIp", ConditionOperator.CONTAINS, "113")).isTrue();
        assertThat(test(e, "hour", ConditionOperator.IN, "3")).isTrue();
        assertThat(test(e, "resource", ConditionOperator.EQUALS, "x")).isFalse(); // null attribute
    }

    @Test
    void cidrHandling() {
        assertThat(test(event("10.1.2.3", "u"), "sourceIp", ConditionOperator.IN_CIDR, "10.0.0.0/8")).isTrue();
        assertThat(test(event("10.1.2.3", "u"), "sourceIp", ConditionOperator.NOT_IN_CIDR, "10.0.0.0/8")).isFalse();
        assertThat(test(event("8.8.8.8", "u"), "sourceIp", ConditionOperator.NOT_IN_CIDR, "10.0.0.0/8")).isTrue();
        // Non-IPv4 or missing values are never "external".
        assertThat(test(event("::1", "u"), "sourceIp", ConditionOperator.NOT_IN_CIDR, "10.0.0.0/8")).isFalse();
        assertThat(test(event(null, "u"), "sourceIp", ConditionOperator.NOT_IN_CIDR, "10.0.0.0/8")).isFalse();
        assertThat(Cidr.isValid("10.0.0.0/33")).isFalse();
        assertThat(Cidr.isValid("10.0.0.0/8")).isTrue();
    }

    @Test
    void referenceSetDelegatesToLookup() {
        ReferenceLookup lookup = mock(ReferenceLookup.class);
        when(lookup.contains(anyLong(), anyString(), anyString())).thenReturn(false);
        when(lookup.contains(1L, "Blocked IPs", "203.0.113.9")).thenReturn(true);
        when(lookupProvider.getIfAvailable()).thenReturn(lookup);
        assertThat(test(event("203.0.113.9", "u"), "sourceIp", ConditionOperator.IN_REFERENCE_SET, "Blocked IPs")).isTrue();
        assertThat(test(event("198.51.100.1", "u"), "sourceIp", ConditionOperator.IN_REFERENCE_SET, "Blocked IPs")).isFalse();
        assertThat(test(event("198.51.100.1", "u"), "sourceIp", ConditionOperator.NOT_IN_REFERENCE_SET, "Blocked IPs")).isTrue();
    }

    @Test
    void ruleWithoutBlocksAlwaysMatchesAndMissingBlockFailsClosed() {
        SecurityEvent e = event("8.8.8.8", "u");
        DetectionRule plain = RuleTestFixtures.rule("BRUTE_FORCE", "{\"threshold\":3}", Severity.HIGH, "T1110");
        assertThat(matcher.matches(e, plain)).isTrue();

        DetectionRule withBlock = RuleTestFixtures.rule("BRUTE_FORCE",
                "{\"buildingBlocks\":[\"External\"]}", Severity.HIGH, "T1110");
        when(repo.findByOrg_IdAndName(eq(1L), eq("External"))).thenReturn(Optional.empty());
        assertThat(matcher.matches(e, withBlock)).isFalse();

        matcher.evict();
        when(repo.findByOrg_IdAndName(eq(1L), eq("External"))).thenReturn(Optional.of(BuildingBlock.builder()
                .name("External").conditions("[{\"field\":\"sourceIp\",\"op\":\"NOT_IN_CIDR\",\"values\":[\"10.0.0.0/8\"]}]")
                .build()));
        assertThat(matcher.matches(e, withBlock)).isTrue();
        assertThat(matcher.matches(event("10.0.0.5", "u"), withBlock)).isFalse();
    }
}
