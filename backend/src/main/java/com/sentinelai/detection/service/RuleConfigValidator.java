package com.sentinelai.detection.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.common.exception.BadRequestException;
import com.sentinelai.detection.buildingblock.BuildingBlockService;
import com.sentinelai.detection.engine.DetectionRuleEvaluator;
import com.sentinelai.detection.engine.GroupBy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Domain validation for rule edits, so a bad save can't break detection at runtime: the rule type
 * must have an evaluator, the config must be a small JSON object with in-range numbers and a known
 * {@code groupBy}, and every referenced building block must exist in the org.
 */
@Component
public class RuleConfigValidator {

    static final int MAX_CONFIG_CHARS = 4096;

    /** Numeric config keys and their inclusive bounds. */
    private static final Map<String, long[]> BOUNDS = Map.ofEntries(
            Map.entry("threshold", new long[]{1, 100_000}),
            Map.entry("windowSeconds", new long[]{1, 7 * 24 * 3600}),
            Map.entry("distinctUsers", new long[]{1, 10_000}),
            Map.entry("minSecondsBetweenCountries", new long[]{1, 7 * 24 * 3600}),
            Map.entry("oddHourStart", new long[]{0, 23}),
            Map.entry("oddHourEnd", new long[]{0, 23}),
            Map.entry("minBytes", new long[]{1, Long.MAX_VALUE}),
            Map.entry("minSamples", new long[]{1, 10_000}),
            Map.entry("lookbackDays", new long[]{1, 365}),
            Map.entry("toleranceHours", new long[]{0, 12}),
            Map.entry("maxSharePercent", new long[]{0, 100}),
            Map.entry("minCount", new long[]{1, 100_000}));

    private final Set<String> ruleTypes;
    private final BuildingBlockService buildingBlocks;
    private final ObjectMapper objectMapper;

    public RuleConfigValidator(List<DetectionRuleEvaluator> evaluators, BuildingBlockService buildingBlocks,
                               ObjectMapper objectMapper) {
        this.ruleTypes = evaluators.stream().map(DetectionRuleEvaluator::type)
                .collect(Collectors.toCollection(TreeSet::new));
        this.buildingBlocks = buildingBlocks;
        this.objectMapper = objectMapper;
    }

    public Set<String> ruleTypes() {
        return ruleTypes;
    }

    /** @return the normalized config JSON ({@code {}} when blank) */
    public String validate(Long orgId, String ruleType, String config) {
        if (!ruleTypes.contains(ruleType)) {
            throw new BadRequestException("Unknown rule type: " + ruleType);
        }
        if (config == null || config.isBlank()) {
            return "{}";
        }
        if (config.length() > MAX_CONFIG_CHARS) {
            throw new BadRequestException("Rule config is too large (max " + MAX_CONFIG_CHARS + " chars)");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(config);
        } catch (Exception e) {
            throw new BadRequestException("Rule config must be valid JSON");
        }
        if (node == null || !node.isObject()) {
            throw new BadRequestException("Rule config must be a JSON object");
        }
        BOUNDS.forEach((key, range) -> {
            JsonNode v = node.get(key);
            if (v != null && !v.isNull()) {
                if (!v.canConvertToLong() || v.asLong() < range[0] || v.asLong() > range[1]) {
                    throw new BadRequestException("'" + key + "' must be a whole number between "
                            + range[0] + " and " + range[1]);
                }
            }
        });
        JsonNode groupBy = node.get("groupBy");
        if (groupBy != null && !groupBy.isNull() && GroupBy.fromString(groupBy.asText(), null) == null) {
            throw new BadRequestException("'groupBy' must be username, sourceIp or entityKey");
        }
        JsonNode blocks = node.get("buildingBlocks");
        if (blocks != null && !blocks.isNull()) {
            if (!blocks.isArray()) {
                throw new BadRequestException("'buildingBlocks' must be a list of building block names");
            }
            for (JsonNode b : blocks) {
                if (!b.isTextual() || !buildingBlocks.exists(orgId, b.asText())) {
                    throw new BadRequestException("Unknown building block: " + b.asText());
                }
            }
        }
        return node.toString();
    }
}
