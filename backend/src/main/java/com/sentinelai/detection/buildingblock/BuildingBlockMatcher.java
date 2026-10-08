package com.sentinelai.detection.buildingblock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.event.domain.SecurityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides whether an event satisfies every building block a rule references
 * ({@code config.buildingBlocks}). Conditions are parsed once per block and cached; the cache is
 * evicted whenever a block changes. A referenced block that no longer exists fails closed (the rule
 * doesn't fire) and is logged, so deleting a block can't silently widen a rule.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuildingBlockMatcher {

    private final BuildingBlockRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ReferenceLookup> referenceLookup;

    private final Map<String, Optional<List<Condition>>> cache = new ConcurrentHashMap<>();

    /** Names of the building blocks referenced by a rule's config (empty when none). */
    public List<String> referencedBy(String ruleConfig) {
        if (ruleConfig == null || ruleConfig.isBlank()) {
            return List.of();
        }
        try {
            JsonNode refs = objectMapper.readTree(ruleConfig).path("buildingBlocks");
            List<String> out = new ArrayList<>();
            if (refs.isArray()) {
                refs.forEach(n -> out.add(n.asText()));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    public boolean matches(SecurityEvent event, DetectionRule rule) {
        List<String> names = referencedBy(rule.getConfig());
        if (names.isEmpty()) {
            return true;
        }
        Long orgId = event.getOrg().getId();
        for (String name : names) {
            Optional<List<Condition>> conditions = cache.computeIfAbsent(orgId + ":" + name, k -> load(orgId, name));
            if (conditions.isEmpty()) {
                log.warn("Rule {} references missing building block '{}'; not firing", rule.getId(), name);
                return false;
            }
            for (Condition c : conditions.get()) {
                if (!test(orgId, event, c)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void evict() {
        cache.clear();
    }

    private Optional<List<Condition>> load(Long orgId, String name) {
        return repository.findByOrg_IdAndName(orgId, name).map(b -> parse(b.getConditions()));
    }

    public List<Condition> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Condition>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid building block conditions: " + e.getMessage(), e);
        }
    }

    boolean test(Long orgId, SecurityEvent event, Condition c) {
        String actual = ConditionField.parse(c.field()).valueOf(event);
        List<String> values = c.values() == null ? List.of() : c.values();
        String a = actual == null ? null : actual.toLowerCase(Locale.ROOT);
        return switch (c.op()) {
            case EQUALS -> a != null && !values.isEmpty() && a.equals(values.get(0).toLowerCase(Locale.ROOT));
            case NOT_EQUALS -> a == null || values.isEmpty() || !a.equals(values.get(0).toLowerCase(Locale.ROOT));
            case IN -> a != null && values.stream().anyMatch(v -> a.equals(v.toLowerCase(Locale.ROOT)));
            case NOT_IN -> a == null || values.stream().noneMatch(v -> a.equals(v.toLowerCase(Locale.ROOT)));
            case CONTAINS -> a != null && values.stream().anyMatch(v -> a.contains(v.toLowerCase(Locale.ROOT)));
            case STARTS_WITH -> a != null && values.stream().anyMatch(v -> a.startsWith(v.toLowerCase(Locale.ROOT)));
            case IN_CIDR -> actual != null && values.stream().anyMatch(cidr -> Cidr.contains(cidr, actual));
            case NOT_IN_CIDR -> actual != null && Cidr.isIpv4(actual)
                    && values.stream().noneMatch(cidr -> Cidr.contains(cidr, actual));
            case IN_REFERENCE_SET -> actual != null && inAnySet(orgId, values, actual);
            case NOT_IN_REFERENCE_SET -> actual == null || !inAnySet(orgId, values, actual);
        };
    }

    private boolean inAnySet(Long orgId, List<String> sets, String value) {
        ReferenceLookup lookup = referenceLookup.getIfAvailable();
        return lookup != null && sets.stream().anyMatch(s -> lookup.contains(orgId, s, value));
    }
}
