package com.sentinelai.incident.dto;

/** assigneeId may be null to unassign. */
public record AssignRequest(Long assigneeId) {
}
