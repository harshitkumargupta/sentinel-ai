package com.sentinelai.logsource;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Per-source volume in one grouped query: total events and events ingested since a cut-off. */
@Component
@RequiredArgsConstructor
public class LogSourceStats {

    public record Volume(long total, long recent) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public Map<Long, Volume> volumes(Long orgId, Instant recentSince) {
        Map<Long, Volume> out = new HashMap<>();
        jdbc.query("""
                select site_id, count(*) as total, sum(case when ingested_at >= :since then 1 else 0 end) as recent
                from security_events where org_id = :org and site_id is not null group by site_id
                """,
                new MapSqlParameterSource("org", orgId).addValue("since", Timestamp.from(recentSince)),
                rs -> {
                    out.put(rs.getLong("site_id"), new Volume(rs.getLong("total"), rs.getLong("recent")));
                });
        return out;
    }
}
