package com.sentinelai.detection.rule;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-user behaviour baselines computed from stored events with plain SQL statistics (no ML):
 * login-hour histogram, known countries / /24 networks, and hourly failed-login counts. Every query
 * looks only at events strictly before the event being evaluated.
 */
@Component
@RequiredArgsConstructor
public class UserBaselineQueries {

    static final List<String> LOGIN_TYPES = List.of("LOGIN_SUCCESS", "SUSPICIOUS_LOGIN");

    private final NamedParameterJdbcTemplate jdbc;

    private MapSqlParameterSource p(Long orgId, String user, Instant since, Instant before) {
        return new MapSqlParameterSource("org", orgId).addValue("user", user).addValue("types", LOGIN_TYPES)
                .addValue("since", Timestamp.from(since)).addValue("before", Timestamp.from(before));
    }

    /** UTC hour → number of the user's prior logins in that hour. */
    public Map<Integer, Long> loginHours(Long orgId, String user, Instant since, Instant before) {
        Map<Integer, Long> out = new HashMap<>();
        jdbc.query("""
                select hour(event_timestamp) h, count(*) c from security_events
                where org_id = :org and username = :user and event_type in (:types)
                  and event_timestamp >= :since and event_timestamp < :before
                group by hour(event_timestamp)
                """, p(orgId, user, since, before), rs -> { out.put(rs.getInt("h"), rs.getLong("c")); });
        return out;
    }

    public long priorLogins(Long orgId, String user, Instant since, Instant before) {
        Long n = jdbc.queryForObject("""
                select count(*) from security_events where org_id = :org and username = :user
                  and event_type in (:types) and event_timestamp >= :since and event_timestamp < :before
                """, p(orgId, user, since, before), Long.class);
        return n == null ? 0 : n;
    }

    public boolean seenCountry(Long orgId, String user, String country, Instant since, Instant before) {
        Long n = jdbc.queryForObject("""
                select count(*) from security_events where org_id = :org and username = :user
                  and event_type in (:types) and geo_country = :country
                  and event_timestamp >= :since and event_timestamp < :before
                """, p(orgId, user, since, before).addValue("country", country), Long.class);
        return n != null && n > 0;
    }

    /** True if the user logged in before from the same IPv4 /24 (prefix "a.b.c."). */
    public boolean seenSubnet(Long orgId, String user, String prefix, Instant since, Instant before) {
        Long n = jdbc.queryForObject("""
                select count(*) from security_events where org_id = :org and username = :user
                  and event_type in (:types) and source_ip like :prefix
                  and event_timestamp >= :since and event_timestamp < :before
                """, p(orgId, user, since, before).addValue("prefix", prefix.replace("%", "") + "%"), Long.class);
        return n != null && n > 0;
    }

    /** The user's FAILED_LOGIN counts per hour bucket over the lookback (only non-empty hours). */
    public List<Long> hourlyFailures(Long orgId, String user, Instant since, Instant before) {
        return jdbc.query("""
                select count(*) from security_events where org_id = :org and username = :user
                  and event_type = 'FAILED_LOGIN' and event_timestamp >= :since and event_timestamp < :before
                group by date(event_timestamp), hour(event_timestamp)
                """, p(orgId, user, since, before), (rs, n) -> rs.getLong(1));
    }

    public long failuresBetween(Long orgId, String user, Instant since, Instant upTo) {
        Long n = jdbc.queryForObject("""
                select count(*) from security_events where org_id = :org and username = :user
                  and event_type = 'FAILED_LOGIN' and event_timestamp >= :since and event_timestamp <= :before
                """, p(orgId, user, since, upTo), Long.class);
        return n == null ? 0 : n;
    }
}
