package com.sentinelai.adminrisk;

import java.util.Set;

/**
 * An admin's learned baseline. Empty sets mean "no signal yet" — factors that compare against a
 * baseline stay silent rather than flagging everything for a brand-new admin.
 */
public record AdminBaselines(
        Set<String> knownCountries,
        Set<String> knownIps,
        Set<String> knownDevices,
        Set<Integer> typicalHours,
        Set<Long> knownSites) {

    public boolean hasCountryBaseline() {
        return knownCountries != null && !knownCountries.isEmpty();
    }
}
