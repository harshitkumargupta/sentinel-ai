package com.sentinelai.risk;

import com.sentinelai.common.domain.Severity;

import java.util.List;

public record RiskResult(int score, Severity severity, List<FactorResult> breakdown) {
}
