package com.sentinelai.detection.buildingblock;

/** How a condition compares an event attribute with its values (case-insensitive for text). */
public enum ConditionOperator {
    EQUALS, NOT_EQUALS, IN, NOT_IN, CONTAINS, STARTS_WITH, IN_CIDR, NOT_IN_CIDR, IN_REFERENCE_SET, NOT_IN_REFERENCE_SET
}
