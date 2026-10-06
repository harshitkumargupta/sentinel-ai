package com.sentinelai.risk;

import com.sentinelai.alert.domain.Alert;
import com.sentinelai.event.domain.SecurityEvent;

import java.util.List;

/** Inputs a {@link RiskFactor} scores over: the incident's alerts and events, plus config. */
public record RiskContext(List<Alert> alerts, List<SecurityEvent> events, RiskProperties props) {
}
