package com.sentinelai.detection.buildingblock;

import java.util.List;

/** One building-block test: {@code field op values} (e.g. sourceIp NOT_IN_CIDR [10.0.0.0/8]). */
public record Condition(String field, ConditionOperator op, List<String> values) {
}
