package com.sentinelai.offense;

import java.util.Collection;
import java.util.List;

/**
 * Credibility input from threat intelligence: which of an offense's IPs appear on a blocklist. The
 * threat-intel module implements it; without one, nothing matches.
 */
public interface ThreatIntelSignal {

    record Match(String ip, String list) {
    }

    List<Match> matches(Collection<String> ips);
}
