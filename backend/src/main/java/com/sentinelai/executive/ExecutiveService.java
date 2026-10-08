package com.sentinelai.executive;

import com.sentinelai.report.ReportTable;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executive view: a 0–100 security posture score (formula and each penalty shown), MTTD and MTTR,
 * incidents by severity per day, top risks, open vs resolved, and a plain-language summary written
 * by an offline template — no AI service.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveService {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public record Penalty(String factor, double points, String detail) {
    }

    public record DayCount(String day, long low, long medium, long high, long critical) {
    }

    public record Risk(Long incidentId, String title, String severity, Integer riskScore, String status, String assignee) {
    }

    public record Summary(int days, int postureScore, String grade, List<Penalty> penalties, String formula,
                          Double mttdMinutes, Double mttrHours, long incidents, long open, long resolved,
                          List<DayCount> bySeverity, List<Risk> topRisks, Double coveragePct, List<String> plainLanguage) {
    }

    @Transactional(readOnly = true)
    public Summary summary(Long orgId, int days) {
        int d = Math.max(1, Math.min(days, 365));
        Instant now = clock.instant();
        Instant from = now.minus(Duration.ofDays(d));
        MapSqlParameterSource p = new MapSqlParameterSource("org", orgId).addValue("from", Timestamp.from(from));

        Map<String, Object> c = jdbc.queryForMap("""
                select count(*) total,
                  sum(case when status in ('RESOLVED','CLOSED','FALSE_POSITIVE') then 1 else 0 end) resolved,
                  sum(case when status not in ('RESOLVED','CLOSED','FALSE_POSITIVE') and severity = 'CRITICAL' then 1 else 0 end) open_critical,
                  sum(case when status not in ('RESOLVED','CLOSED','FALSE_POSITIVE') and severity = 'HIGH' then 1 else 0 end) open_high,
                  sum(case when status not in ('RESOLVED','CLOSED','FALSE_POSITIVE') and assigned_to is null then 1 else 0 end) unassigned,
                  avg(case when coalesce(resolved_at, closed_at) is not null
                      then timestampdiff(SECOND, created_at, coalesce(resolved_at, closed_at)) end) mttr_s
                from incidents where org_id = :org and created_at >= :from
                """, p);
        long total = n(c.get("total"));
        long resolved = n(c.get("resolved"));
        long openCritical = n(c.get("open_critical"));
        long openHigh = n(c.get("open_high"));
        long unassigned = n(c.get("unassigned"));
        Double mttrH = c.get("mttr_s") == null ? null : ((Number) c.get("mttr_s")).doubleValue() / 3600.0;
        Double mttdMin = jdbc.queryForObject("""
                select avg(timestampdiff(SECOND, x.first_event, x.created_at)) / 60.0 from (
                  select i.id, i.created_at, min(e.event_timestamp) first_event from incidents i
                  join incident_events ie on ie.incident_id = i.id join security_events e on e.id = ie.event_id
                  where i.org_id = :org and i.created_at >= :from group by i.id, i.created_at) x
                """, p, Double.class);
        long openVulns = n(jdbc.queryForObject("""
                select count(*) from vulnerabilities where org_id = :org and status = 'OPEN' and severity in ('HIGH','CRITICAL')
                """, p, Long.class));
        List<Double> cov = jdbc.queryForList("select coverage_pct from coverage_runs where org_id = :org order by id desc limit 1", p, Double.class);
        Double coverage = cov.isEmpty() ? null : cov.get(0);

        List<Penalty> penalties = new ArrayList<>();
        penalties.add(new Penalty("Open critical incidents", Math.min(30, openCritical * 10), openCritical + " × 10 (max 30)"));
        penalties.add(new Penalty("Open high incidents", Math.min(20, openHigh * 5), openHigh + " × 5 (max 20)"));
        double mttrPenalty = mttrH == null ? 0 : mttrH > 24 ? 10 : mttrH > 4 ? 5 : 0;
        penalties.add(new Penalty("Slow response (MTTR)", mttrPenalty,
                mttrH == null ? "no resolved incidents yet" : "%.1f h (>4 h = 5, >24 h = 10)".formatted(mttrH)));
        double covPenalty = coverage == null ? 10 : Math.min(20, (100 - coverage) * 0.2);
        penalties.add(new Penalty("Detection coverage gaps", round1(covPenalty),
                coverage == null ? "no coverage test run yet (10)" : "(100 − %.1f%%) × 0.2 (max 20)".formatted(coverage)));
        penalties.add(new Penalty("Open high/critical vulnerabilities", Math.min(10, openVulns * 2), openVulns + " × 2 (max 10)"));
        penalties.add(new Penalty("Unassigned open incidents", Math.min(10, unassigned), unassigned + " × 1 (max 10)"));
        double totalPenalty = penalties.stream().mapToDouble(Penalty::points).sum();
        int score = (int) Math.max(0, Math.min(100, Math.round(100 - totalPenalty)));
        String grade = score >= 85 ? "Strong" : score >= 70 ? "Fair" : score >= 50 ? "At risk" : "Critical";

        Map<String, long[]> days0 = new LinkedHashMap<>();
        LocalDate start = from.atOffset(ZoneOffset.UTC).toLocalDate();
        for (LocalDate x = start; !x.isAfter(now.atOffset(ZoneOffset.UTC).toLocalDate()); x = x.plusDays(1)) {
            days0.put(x.toString(), new long[4]);
        }
        jdbc.query("""
                select date(created_at) d, severity s, count(*) c from incidents
                where org_id = :org and created_at >= :from group by date(created_at), severity
                """, p, rs -> {
                    long[] row = days0.get(rs.getString("d"));
                    if (row != null) {
                        int idx = switch (rs.getString("s")) {
                            case "LOW" -> 0;
                            case "MEDIUM" -> 1;
                            case "HIGH" -> 2;
                            default -> 3;
                        };
                        row[idx] += rs.getLong("c");
                    }
                });
        List<DayCount> bySeverity = days0.entrySet().stream()
                .map(e -> new DayCount(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3])).toList();

        List<Risk> top = jdbc.query("""
                select i.id, i.title, i.severity, i.risk_score, i.status, u.username from incidents i
                left join users u on u.id = i.assigned_to
                where i.org_id = :org and i.status not in ('RESOLVED','CLOSED','FALSE_POSITIVE')
                order by i.risk_score desc, i.id desc limit 5
                """, p, (rs, k) -> new Risk(rs.getLong(1), rs.getString(2), rs.getString(3), (Integer) rs.getObject(4),
                rs.getString(5), rs.getString(6)));

        String formula = "score = 100 − (" + penalties.stream().map(x -> String.valueOf(x.points())).reduce((a, b) -> a + " + " + b).orElse("0")
                + ") = " + score;
        return new Summary(d, score, grade, penalties, formula, mttdMin == null ? null : round1(mttdMin),
                mttrH == null ? null : round1(mttrH), total, total - resolved, resolved, bySeverity, top, coverage,
                plainLanguage(d, score, grade, total, total - resolved, openCritical, openHigh, mttdMin, mttrH, coverage,
                        openVulns, unassigned, top, penalties));
    }

    /** Offline template: short sentences a non-technical reader can follow. */
    static List<String> plainLanguage(int days, int score, String grade, long total, long open, long openCritical, long openHigh,
                                      Double mttdMin, Double mttrH, Double coverage, long vulns, long unassigned,
                                      List<Risk> top, List<Penalty> penalties) {
        List<String> out = new ArrayList<>();
        out.add("Overall security posture is %s (%d out of 100) for the last %d days.".formatted(grade.toLowerCase(), score, days));
        out.add(total == 0 ? "No security incidents were raised in this period."
                : "SentinelAI raised %d incident%s; %d %s still open%s.".formatted(total, total == 1 ? "" : "s", open,
                        open == 1 ? "is" : "are", openCritical + openHigh > 0
                                ? ", including %d serious (critical or high) one%s".formatted(openCritical + openHigh, openCritical + openHigh == 1 ? "" : "s") : ""));
        if (mttdMin != null) {
            out.add("On average, attacks were spotted %s after they started.".formatted(mttdMin < 1 ? "within a minute" : "%.0f minutes".formatted(mttdMin)));
        }
        out.add(mttrH == null ? "No incident has been closed yet, so response time can't be measured."
                : "Incidents were resolved in %s on average.".formatted(mttrH < 1 ? "under an hour" : "%.1f hours".formatted(mttrH)));
        out.add(coverage == null ? "The detection coverage test hasn't been run yet; running it shows which attack types we would miss."
                : "Our tests show we detect %.0f%% of the simulated attack types we checked.".formatted(coverage));
        if (!top.isEmpty()) {
            Risk r = top.get(0);
            out.add("The biggest open risk is “%s” (%s severity)%s.".formatted(r.title(), r.severity().toLowerCase(),
                    r.assignee() == null ? ", which nobody is assigned to yet" : ", which " + r.assignee() + " is handling"));
        }
        Penalty worst = penalties.stream().max(java.util.Comparator.comparingDouble(Penalty::points)).orElse(null);
        if (worst != null && worst.points() > 0) {
            out.add("The quickest way to improve the score: address “" + worst.factor().toLowerCase() + "”.");
        }
        if (vulns > 0) {
            out.add("%d serious software vulnerabilit%s on our systems still need patching.".formatted(vulns, vulns == 1 ? "y" : "ies"));
        }
        if (unassigned > 0) {
            out.add("%d open incident%s ha%s no owner.".formatted(unassigned, unassigned == 1 ? "" : "s", unassigned == 1 ? "s" : "ve"));
        }
        return out;
    }

    /** As a report table for the shared PDF renderer. */
    public ReportTable table(Summary s) {
        List<String> summary = new ArrayList<>(s.plainLanguage());
        summary.add("Posture formula: " + s.formula());
        s.penalties().forEach(p -> summary.add("  " + p.factor() + ": -" + p.points() + " (" + p.detail() + ")"));
        summary.add("MTTD: " + (s.mttdMinutes() == null ? "n/a" : s.mttdMinutes() + " min") + "; MTTR: "
                + (s.mttrHours() == null ? "n/a" : s.mttrHours() + " h") + "; open " + s.open() + " / resolved " + s.resolved());
        List<List<String>> rows = s.topRisks().stream().map(r -> List.of("#" + r.incidentId(), r.title(), r.severity(),
                String.valueOf(r.riskScore()), r.status(), r.assignee() == null ? "unassigned" : r.assignee())).toList();
        Instant now = clock.instant();
        return new ReportTable("Executive Security Summary", now.minus(Duration.ofDays(s.days())), now, summary,
                List.of("Incident", "Top open risks", "Severity", "Risk", "Status", "Owner"), rows, false);
    }

    private static long n(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
