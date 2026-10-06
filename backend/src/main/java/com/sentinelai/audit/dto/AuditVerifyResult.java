package com.sentinelai.audit.dto;

public record AuditVerifyResult(boolean valid, Long firstBrokenId) {
}
