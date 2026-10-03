package com.sentinelai.detection.support;

import com.sentinelai.common.domain.Organization;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;

import java.time.Instant;

/** Builders for detached domain objects used by rule unit tests. */
public final class RuleTestFixtures {

    public static final Organization ORG = Organization.builder().id(1L).name("Default Org").build();

    private RuleTestFixtures() {
    }

    public static DetectionRule rule(String type, String config, Severity severity, String mitre) {
        return DetectionRule.builder()
                .id(1L).org(ORG).name(type).ruleType(type).config(config)
                .enabled(true).severity(severity).mitreTechnique(mitre).version(1).build();
    }

    public static SecurityEvent.SecurityEventBuilder event(EventType type) {
        return SecurityEvent.builder().id(100L).org(ORG).eventType(type).severity(Severity.LOW)
                .honeytoken(false).eventTimestamp(Instant.parse("2026-02-01T12:00:00Z"));
    }
}
