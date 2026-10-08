package com.sentinelai.detection.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.detection.domain.DetectionRule;
import com.sentinelai.detection.engine.AlertDraft;
import com.sentinelai.detection.engine.ConfigReader;
import com.sentinelai.detection.engine.DetectionRuleEvaluator;
import com.sentinelai.detection.engine.RuleContext;
import com.sentinelai.event.domain.SecurityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * UBA: a login from a country, or an IPv4 /24 network, this user has not logged in from in
 * {@code lookbackDays}; needs {@code minSamples} prior logins. config also toggles
 * {@code checkCountry} / {@code checkSubnet} (1/0).
 */
@Component
@RequiredArgsConstructor
public class NewLoginLocationRule implements DetectionRuleEvaluator {

    private final ObjectMapper objectMapper;
    private final UserBaselineQueries baselines;

    @Override
    public String type() {
        return RuleTypes.UBA_NEW_LOCATION;
    }

    @Override
    public Optional<AlertDraft> evaluate(SecurityEvent event, DetectionRule rule, RuleContext ctx) {
        if (event.getUsername() == null || !UserBaselineQueries.LOGIN_TYPES.contains(event.getEventType().name())) {
            return Optional.empty();
        }
        ConfigReader cfg = ConfigReader.of(rule.getConfig(), objectMapper);
        int minSamples = cfg.getInt("minSamples", 5);
        int lookback = cfg.getInt("lookbackDays", 30);
        Instant at = event.getEventTimestamp();
        Instant since = at.minusSeconds(lookback * 86400L);
        Long org = event.getOrg().getId();
        String user = event.getUsername();
        if (baselines.priorLogins(org, user, since, at) < minSamples) {
            return Optional.empty();
        }
        List<String> news = new ArrayList<>();
        if (cfg.getInt("checkCountry", 1) == 1 && event.getGeoCountry() != null
                && !baselines.seenCountry(org, user, event.getGeoCountry(), since, at)) {
            news.add("country " + event.getGeoCountry());
        }
        String ip = event.getSourceIp();
        if (cfg.getInt("checkSubnet", 1) == 1 && ip != null && ip.chars().filter(c -> c == '.').count() == 3) {
            String prefix = ip.substring(0, ip.lastIndexOf('.') + 1);
            if (!baselines.seenSubnet(org, user, prefix, since, at)) {
                news.add("network " + prefix + "0/24");
            }
        }
        if (news.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AlertDraft(rule.getSeverity(), rule.getMitreTechnique(),
                "New login location for " + user + ": " + String.join(" and ", news) + " (not seen in " + lookback + " days)",
                "user:" + user, List.of(event.getId())));
    }
}
