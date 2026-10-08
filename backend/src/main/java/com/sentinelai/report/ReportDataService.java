package com.sentinelai.report;

import com.sentinelai.common.mitre.MitreCatalog;
import com.sentinelai.threatintel.ThreatIntelService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds report content with org-scoped, bounded SQL aggregations (read-only). One method per
 * {@link ReportType}; every table also carries headline facts for the summary section.
 */
@Service
@RequiredArgsConstructor
public class ReportDataService {

    private final NamedParameterJdbcTemplate jdbc;
    private final MitreCatalog mitre;
    private final ThreatIntelService threatIntel;
    private final ReportProperties props;

    @Transactional(readOnly = true)
    public ReportTable build(Long orgId, ReportType type, Instant from, Instant to) {
        MapSqlParameterSource p = new MapSqlParameterSource("org", orgId)
                .addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to))
                .addValue("max", props.getMaxRows() + 1);
        return switch (type) {
            case INCIDENT_SUMMARY -> incidents(p, type, from, to);
            case TOP_ATTACKERS -> attackers(p, type, from, to);
            case ALERTS_BY_MITRE -> mitre(p, type, from, to);
            case RESPONSE_ACTIONS -> actions(p, type, from, to);
        };
    }

    private ReportTable incidents(MapSqlParameterSource p, ReportType type, Instant from, Instant to) {
        List<List<String>> rows = jdbc.query("""
                select i.id, i.title, i.status, i.severity, i.priority, i.risk_score, u.username,
                       i.created_at, i.resolved_at
                from incidents i left join users u on u.id = i.assigned_to
                where i.org_id = :org and i.created_at between :from and :to
                order by i.created_at desc limit :max
                """, p, (rs, n) -> row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getObject(6), rs.getString(7), ts(rs.getTimestamp(8)), ts(rs.getTimestamp(9))));
        Map<String, Object> agg = jdbc.queryForMap("""
                select count(*) as total,
                       sum(case when status in ('RESOLVED','CLOSED','FALSE_POSITIVE') then 1 else 0 end) as done,
                       sum(case when severity in ('HIGH','CRITICAL') then 1 else 0 end) as high,
                       avg(case when resolved_at is not null then timestampdiff(SECOND, created_at, resolved_at) end) as mttr
                from incidents where org_id = :org and created_at between :from and :to
                """, p);
        List<String> summary = new ArrayList<>();
        long total = num(agg.get("total"));
        summary.add("Incidents opened: " + total);
        summary.add("Resolved / closed / false positive: " + num(agg.get("done")) + "; still open: " + (total - num(agg.get("done"))));
        summary.add("High or critical severity: " + num(agg.get("high")));
        Object mttr = agg.get("mttr");
        summary.add("Mean time to resolve: " + (mttr == null ? "n/a (none resolved)" : human((long) ((Number) mttr).doubleValue())));
        summary.addAll(counts("select status, count(*) from incidents where org_id = :org and created_at between :from and :to group by status order by 2 desc", p, "By status: "));
        summary.addAll(counts("select severity, count(*) from incidents where org_id = :org and created_at between :from and :to group by severity order by 2 desc", p, "By severity: "));
        return table(type, from, to, summary,
                List.of("ID", "Title", "Status", "Severity", "Priority", "Risk", "Assignee", "Created (UTC)", "Resolved (UTC)"), rows);
    }

    private ReportTable attackers(MapSqlParameterSource p, ReportType type, Instant from, Instant to) {
        List<List<String>> rows = jdbc.query("""
                select e.source_ip, max(e.geo_country), count(distinct e.id), count(distinct ie.incident_id),
                       sum(case when e.outcome = 'FAILURE' then 1 else 0 end), min(e.event_timestamp), max(e.event_timestamp)
                from security_events e
                join incident_events ie on ie.event_id = e.id
                where e.org_id = :org and e.event_timestamp between :from and :to and e.source_ip is not null
                group by e.source_ip
                order by count(distinct ie.incident_id) desc, count(distinct e.id) desc
                limit :max
                """, p, (rs, n) -> {
                    String ip = rs.getString(1);
                    String listed = threatIntel.check(ip).matches().isEmpty() ? "" : "yes";
                    return row(ip, rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), listed,
                            ts(rs.getTimestamp(6)), ts(rs.getTimestamp(7)));
                });
        List<String> summary = new ArrayList<>();
        summary.add("Source IPs involved in incidents: " + rows.size());
        summary.add("On an offline threat-intel list: " + rows.stream().filter(r -> "yes".equals(r.get(5))).count());
        if (!rows.isEmpty()) {
            summary.add("Most active: " + rows.get(0).get(0) + " (" + rows.get(0).get(3) + " incident(s), "
                    + rows.get(0).get(2) + " event(s))");
        }
        return table(type, from, to, summary,
                List.of("Source IP", "Country", "Events", "Incidents", "Failures", "Threat intel", "First seen (UTC)", "Last seen (UTC)"), rows);
    }

    private ReportTable mitre(MapSqlParameterSource p, ReportType type, Instant from, Instant to) {
        List<List<String>> rows = jdbc.query("""
                select coalesce(mitre_technique, ''), count(*), group_concat(distinct rule_type order by rule_type separator ', '),
                       sum(case when severity in ('HIGH','CRITICAL') then 1 else 0 end)
                from alerts where org_id = :org and created_at between :from and :to
                group by coalesce(mitre_technique, '') order by count(*) desc limit :max
                """, p, (rs, n) -> {
                    String id = rs.getString(1);
                    var t = id.isEmpty() ? null : mitre.lookup(id);
                    return row(id.isEmpty() ? "(untagged)" : id, t == null ? "" : t.name(), t == null ? "" : t.tactic(),
                            rs.getLong(2), rs.getLong(4), rs.getString(3));
                });
        long total = rows.stream().mapToLong(r -> Long.parseLong(r.get(3))).sum();
        List<String> summary = new ArrayList<>();
        summary.add("Alerts: " + total + " across " + rows.size() + " technique(s)");
        Map<String, Long> byTactic = new LinkedHashMap<>();
        rows.forEach(r -> byTactic.merge(r.get(2).isEmpty() ? "(none)" : r.get(2), Long.parseLong(r.get(3)), Long::sum));
        summary.add("By tactic: " + byTactic);
        return table(type, from, to, summary,
                List.of("Technique", "Name", "Tactic", "Alerts", "High/critical", "Rules"), rows);
    }

    private ReportTable actions(MapSqlParameterSource p, ReportType type, Instant from, Instant to) {
        List<List<String>> rows = jdbc.query("""
                select pa.id, pa.incident_id, pa.action_type, pa.target_ref, pa.status, pu.username, au.username,
                       pa.created_at, pa.executed_at, pa.rolled_back_at
                from playbook_actions pa
                join incidents i on i.id = pa.incident_id
                left join users pu on pu.id = pa.proposed_by
                left join users au on au.id = pa.approved_by
                where i.org_id = :org and pa.created_at between :from and :to
                order by pa.created_at desc limit :max
                """, p, (rs, n) -> row(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), ts(rs.getTimestamp(8)), ts(rs.getTimestamp(9)), ts(rs.getTimestamp(10))));
        List<String> summary = new ArrayList<>();
        summary.add("Response actions: " + rows.size() + " (all simulated adapters)");
        summary.addAll(counts("""
                select pa.status, count(*) from playbook_actions pa join incidents i on i.id = pa.incident_id
                where i.org_id = :org and pa.created_at between :from and :to group by pa.status order by 2 desc
                """, p, "By status: "));
        return table(type, from, to, summary,
                List.of("Action", "Incident", "Type", "Target", "Status", "Proposed by", "Approved by",
                        "Proposed (UTC)", "Executed (UTC)", "Rolled back (UTC)"), rows);
    }

    // --- helpers ---------------------------------------------------------------------------------

    private ReportTable table(ReportType type, Instant from, Instant to, List<String> summary,
                              List<String> columns, List<List<String>> rows) {
        boolean truncated = rows.size() > props.getMaxRows();
        List<List<String>> kept = truncated ? rows.subList(0, props.getMaxRows()) : rows;
        if (truncated) {
            summary.add("Table truncated to the first " + props.getMaxRows() + " rows.");
        }
        return new ReportTable(type.title(), from, to, summary, columns, List.copyOf(kept), truncated);
    }

    private List<String> counts(String sql, MapSqlParameterSource p, String prefix) {
        List<String> parts = jdbc.query(sql, p, (rs, n) -> rs.getString(1) + " " + rs.getLong(2));
        return parts.isEmpty() ? List.of() : List.of(prefix + String.join(", ", parts));
    }

    private static List<String> row(Object... cells) {
        List<String> r = new ArrayList<>(cells.length);
        for (Object c : cells) {
            r.add(c == null ? "" : c.toString());
        }
        return r;
    }

    private static String ts(Timestamp t) {
        return t == null ? "" : t.toInstant().toString().replace('T', ' ').replaceAll("\\.\\d+Z$", "Z");
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    static String human(long seconds) {
        Duration d = Duration.ofSeconds(seconds);
        if (d.toHours() > 0) {
            return d.toHours() + "h " + d.toMinutesPart() + "m";
        }
        return d.toMinutes() > 0 ? d.toMinutes() + "m " + d.toSecondsPart() + "s" : seconds + "s";
    }
}
