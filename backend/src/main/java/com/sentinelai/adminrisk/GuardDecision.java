package com.sentinelai.adminrisk;

import com.sentinelai.risk.FactorResult;

import java.util.List;

public record GuardDecision(
        Decision decision,
        RiskBand band,
        int score,
        List<FactorResult> breakdown,
        Long pendingActionId,
        String explanation) {

    public enum Decision {
        ALLOW,
        STEP_UP,
        PENDING_APPROVAL,
        BLOCKED
    }

    public boolean allowed() {
        return decision == Decision.ALLOW;
    }
}
