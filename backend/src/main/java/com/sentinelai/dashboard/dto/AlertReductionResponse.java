package com.sentinelai.dashboard.dto;

/** The SOC funnel: raw events → alerts → incidents, and how much noise was reduced. */
public record AlertReductionResponse(long events, long alerts, long incidents, double reductionPct) {
}
