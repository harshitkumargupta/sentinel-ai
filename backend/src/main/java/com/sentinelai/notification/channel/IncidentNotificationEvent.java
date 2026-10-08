package com.sentinelai.notification.channel;

import com.sentinelai.common.domain.Severity;

import java.util.Set;

/** Published by correlation when an incident is created or escalates; delivered after commit, async. */
public record IncidentNotificationEvent(Long orgId, Long incidentId, String title, Severity severity,
                                        int riskScore, boolean created, Set<String> ruleTypes) {
}
