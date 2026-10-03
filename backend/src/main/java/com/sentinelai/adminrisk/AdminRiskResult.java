package com.sentinelai.adminrisk;

import com.sentinelai.risk.FactorResult;

import java.util.List;

public record AdminRiskResult(int score, RiskBand band, List<FactorResult> breakdown) {
}
