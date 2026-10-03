package com.sentinelai.risk;

/** One risk factor's contribution: a name, points added, and a human-readable reason. */
public record FactorResult(String name, int points, String reason) {

    public static FactorResult none(String name, String reason) {
        return new FactorResult(name, 0, reason);
    }
}
