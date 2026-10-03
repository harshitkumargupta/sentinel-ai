package com.sentinelai.risk;

/**
 * A pluggable contributor to an incident's risk score. Add a factor by adding a bean implementing
 * this interface — {@code RiskService} composes all of them deterministically. Weights come from
 * {@link RiskProperties}.
 */
public interface RiskFactor {

    FactorResult score(RiskContext context);
}
