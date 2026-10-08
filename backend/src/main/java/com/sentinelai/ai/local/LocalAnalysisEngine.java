package com.sentinelai.ai.local;

import com.sentinelai.ai.context.IncidentContext;
import com.sentinelai.ai.context.IncidentContext.EventSummary;
import com.sentinelai.ai.pipeline.AnalysisOutput;
import com.sentinelai.ai.pipeline.AnalysisOutput.Affected;
import com.sentinelai.ai.pipeline.AnalysisOutput.Claim;
import com.sentinelai.ai.pipeline.AnalysisOutput.MitreMapping;
import com.sentinelai.ai.pipeline.AnalysisOutput.Recommendation;
import com.sentinelai.ai.pipeline.AnalysisOutput.Report;
import com.sentinelai.ai.pipeline.AnalysisOutput.TimelineEntry;
import com.sentinelai.common.mitre.MitreCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic, offline incident analyst: a rule/template engine that turns an
 * {@link IncidentContext} (real events, alerts, risk breakdown) into a full analysis — what
 * happened, a timeline, affected users/IPs/hosts, MITRE mapping, severity reasoning and
 * allow-listed recommendations whose targets all come from the evidence. It needs no network and no
 * key, and it only ever reads structured fields, so injected text in event data cannot steer it.
 */
@Component
@RequiredArgsConstructor
public class LocalAnalysisEngine {

    public static final String MODEL_NAME = "sentinel-local-v1";

    private static final int MAX_IDS_PER_CLAIM = 10;
    private static final int MAX_RECOMMENDATIONS = 4;

    private final MitreCatalog mitreCatalog;

    /** One "stage" of the incident: all events of one type, in order of first occurrence. */
    record Stage(String type, List<EventSummary> events) {

        String firstAt() {
            return events.get(0).timestamp();
        }

        List<Long> ids() {
            return events.stream().map(EventSummary::id).limit(MAX_IDS_PER_CLAIM).toList();
        }

        Set<String> distinct(Function<EventSummary, String> f) {
            return events.stream().map(f).filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    public AnalysisOutput analyze(IncidentContext ctx) {
        List<EventSummary> events = ctx.events() == null ? List.of() : ctx.events();
        if (events.isEmpty()) {
            return new AnalysisOutput("No events are linked to this incident yet, so there is nothing to analyze.",
                    0.1, List.of(), List.of(), List.of(),
                    new Report("No evidence yet.", List.of(), new Affected(List.of(), List.of(), List.of(), List.of()),
                            List.of(), "No events to assess.", List.of("Wait for evidence, then re-run the analysis.")));
        }
        List<Stage> stages = stages(events);
        Affected affected = affected(events);
        List<MitreMapping> mitre = mitre(ctx);

        String whatHappened = whatHappened(ctx, stages);
        List<TimelineEntry> timeline = stages.stream()
                .map(s -> new TimelineEntry(s.firstAt(), describe(s), s.ids()))
                .toList();
        List<Recommendation> recommendations = recommendations(stages, affected, ctx);
        Report report = new Report(whatHappened, timeline, affected, mitre,
                severityReasoning(ctx, mitre), nextSteps(recommendations, affected, ctx));

        List<Claim> claims = stages.stream()
                .map(s -> new Claim(capitalize(describe(s)) + ".", s.ids()))
                .toList();
        double confidence = Math.min(0.9, 0.55 + 0.1 * Math.min(3, stages.size()));

        return new AnalysisOutput(whatHappened, round2(confidence), hypotheses(stages, mitre),
                recommendations, claims, report);
    }

    // --- sections -------------------------------------------------------------------------------

    private List<Stage> stages(List<EventSummary> events) {
        Map<String, List<EventSummary>> byType = new LinkedHashMap<>();
        events.stream()
                .sorted(Comparator.comparing(e -> e.timestamp() == null ? "" : e.timestamp()))
                .forEach(e -> byType.computeIfAbsent(String.valueOf(e.type()), k -> new ArrayList<>()).add(e));
        return byType.entrySet().stream().map(e -> new Stage(e.getKey(), e.getValue())).toList();
    }

    private String whatHappened(IncidentContext ctx, List<Stage> stages) {
        String chain = stages.stream().map(this::describe).collect(Collectors.joining(", then "));
        return "Incident #%d (%s, risk %s/100): %s.".formatted(
                ctx.incidentId(), ctx.severity(), ctx.riskScore() == null ? "?" : ctx.riskScore(), chain);
    }

    /** Plain-language description of one stage, built only from structured fields. */
    String describe(Stage s) {
        int n = s.events().size();
        String users = list(s.distinct(EventSummary::username));
        String ips = list(s.distinct(EventSummary::sourceIp));
        String hosts = list(s.distinct(EventSummary::host));
        String resources = list(s.distinct(EventSummary::resource));
        return switch (s.type()) {
            case "FAILED_LOGIN" -> "%d failed login attempt(s) against %s from %s".formatted(n, users, ips);
            case "LOGIN_SUCCESS" -> "a successful login for %s from %s".formatted(users, ips);
            case "SUSPICIOUS_LOGIN" -> "%d suspicious login(s) for %s from %s".formatted(n, users, ips);
            case "PORT_SCAN" -> "%d port probe(s) from %s against %s (reconnaissance)".formatted(n, ips, resources);
            case "SQL_INJECTION" -> "%d SQL injection attempt(s) from %s against %s".formatted(n, ips, resources);
            case "MALWARE_DETECTED" -> "malware / a suspicious process detected on %s (user %s)".formatted(hosts, users);
            case "PRIVILEGE_ESCALATION" -> "%s gained elevated privileges on %s".formatted(users, hosts.equals("none") ? resources : hosts);
            case "DATA_TRANSFER" -> "%d large outbound data transfer(s) by %s to %s".formatted(n, users, resources);
            case "PHISHING_CLICK" -> "%s clicked a malicious link (%s)".formatted(users, resources);
            case "NETWORK_FLOOD" -> "%d flood request(s) from %d source IP(s) against %s".formatted(
                    n, s.distinct(EventSummary::sourceIp).size(), resources);
            case "API_ABUSE" -> "%d abusive API request(s) from %s".formatted(n, ips);
            case "ABNORMAL_ACCESS" -> "%s accessed %s outside their normal rights".formatted(users, resources);
            case "HONEYTOKEN_ACCESS" -> "a planted honeytoken was used (from %s)".formatted(ips);
            case "PROMPT_INJECTION" -> "%d prompt-injection attempt(s) from %s".formatted(n, ips);
            default -> "%d %s event(s) involving %s".formatted(n, s.type(), users.equals("none") ? ips : users);
        };
    }

    private Affected affected(List<EventSummary> events) {
        return new Affected(
                distinct(events, EventSummary::username),
                distinct(events, EventSummary::sourceIp),
                distinct(events, EventSummary::host),
                distinct(events, EventSummary::geoCountry));
    }

    private List<MitreMapping> mitre(IncidentContext ctx) {
        if (ctx.mitre() == null) {
            return List.of();
        }
        return ctx.mitre().stream().map(mitreCatalog::lookup)
                .map(t -> new MitreMapping(t.id(), t.name(), t.tactic())).toList();
    }

    private String severityReasoning(IncidentContext ctx, List<MitreMapping> mitre) {
        StringBuilder sb = new StringBuilder("Rated %s with a risk score of %s/100."
                .formatted(ctx.severity(), ctx.riskScore() == null ? "?" : ctx.riskScore()));
        List<IncidentContext.RiskFactor> factors = ctx.riskFactors() == null ? List.of()
                : ctx.riskFactors().stream()
                        .sorted(Comparator.comparingInt(IncidentContext.RiskFactor::points).reversed())
                        .limit(3).toList();
        if (!factors.isEmpty()) {
            sb.append(" Main drivers: ").append(factors.stream()
                    .map(f -> "%s (+%d%s)".formatted(f.name(), f.points(),
                            f.reason() == null ? "" : ": " + f.reason()))
                    .collect(Collectors.joining("; "))).append('.');
        }
        Set<String> tactics = mitre.stream().map(MitreMapping::tactic)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (tactics.size() > 1) {
            sb.append(" The activity spans %d ATT&CK tactics (%s), which indicates a multi-stage attack rather than noise."
                    .formatted(tactics.size(), String.join(" → ", tactics)));
        }
        return sb.toString();
    }

    private List<String> hypotheses(List<Stage> stages, List<MitreMapping> mitre) {
        List<String> out = new ArrayList<>();
        Set<String> types = stages.stream().map(Stage::type).collect(Collectors.toSet());
        if (types.contains("FAILED_LOGIN") && types.contains("LOGIN_SUCCESS")) {
            out.add("Credential compromise is likely: repeated failed logins were followed by a successful login.");
        }
        if (types.contains("PRIVILEGE_ESCALATION") || types.contains("DATA_TRANSFER")) {
            out.add("The attacker progressed past initial access (privilege escalation and/or data movement observed).");
        }
        if (types.contains("PHISHING_CLICK") || types.contains("MALWARE_DETECTED")) {
            out.add("A user endpoint may be compromised; treat the host and the user's credentials as untrusted.");
        }
        if (types.contains("NETWORK_FLOOD")) {
            out.add("Volumetric traffic from many sources is consistent with a botnet-driven denial of service.");
        }
        if (mitre.size() > 1 && out.isEmpty()) {
            out.add("Several ATT&CK techniques were observed for the same entity, consistent with an active attack.");
        }
        if (out.isEmpty()) {
            out.add("The repeated " + stages.get(0).type()
                    + " activity for the same entity is more consistent with an attack than with benign use.");
        }
        return out;
    }

    private List<Recommendation> recommendations(List<Stage> stages, Affected affected, IncidentContext ctx) {
        Set<String> types = stages.stream().map(Stage::type).collect(Collectors.toSet());
        List<Recommendation> recs = new ArrayList<>();

        mostActiveExternalIp(stages).ifPresent(ip -> recs.add(new Recommendation("block_ip", ip,
                "Most active external source IP across the incident's events.")));
        affected.hosts().stream().findFirst().ifPresent(host -> recs.add(new Recommendation("isolate_host", host,
                "Endpoint involved in the incident; isolate it to stop lateral movement.")));
        affected.users().stream().findFirst().ifPresent(user -> {
            if (types.contains("PRIVILEGE_ESCALATION") || types.contains("DATA_TRANSFER")) {
                recs.add(new Recommendation("disable_user", user,
                        "Account shows post-compromise activity (privilege escalation / data movement)."));
            }
            if (types.contains("FAILED_LOGIN") || types.contains("PHISHING_CLICK")
                    || types.contains("SUSPICIOUS_LOGIN") || types.contains("LOGIN_SUCCESS")) {
                recs.add(new Recommendation("force_password_reset", user,
                        "Credentials were targeted or possibly exposed; force a reset."));
            }
        });
        String monitorTarget = ctx.entity() != null && ctx.entity().value() != null
                ? ctx.entity().value()
                : affected.ips().stream().findFirst().orElse(null);
        if (monitorTarget != null && recs.size() < MAX_RECOMMENDATIONS) {
            recs.add(new Recommendation("monitor", monitorTarget, "Keep watching this entity for further activity."));
        }
        return recs.stream().limit(MAX_RECOMMENDATIONS).toList();
    }

    private java.util.Optional<String> mostActiveExternalIp(List<Stage> stages) {
        return stages.stream().flatMap(s -> s.events().stream())
                .map(EventSummary::sourceIp)
                .filter(Objects::nonNull)
                .filter(ip -> !isPrivate(ip))
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    private List<String> nextSteps(List<Recommendation> recs, Affected affected, IncidentContext ctx) {
        List<String> steps = new ArrayList<>();
        for (Recommendation r : recs) {
            steps.add(switch (r.action()) {
                case "block_ip" -> "Block " + r.target() + " at the perimeter (Actions → Block IP, dry-run first).";
                case "isolate_host" -> "Isolate host " + r.target() + " from the network and collect a forensic image.";
                case "disable_user" -> "Disable account " + r.target() + " until its activity is reviewed.";
                case "force_password_reset" -> "Force a password reset for " + r.target() + " and revoke active sessions.";
                default -> "Keep monitoring " + r.target() + " for 24 hours.";
            });
        }
        if (!affected.countries().isEmpty()) {
            steps.add("Check whether access from " + String.join(", ", affected.countries()) + " is expected for this organization.");
        }
        steps.add("Record the outcome: mark the incident true or false positive so detection tuning learns from it.");
        return steps;
    }

    // --- helpers ------------------------------------------------------------------------------

    private static List<String> distinct(List<EventSummary> events, Function<EventSummary, String> f) {
        return events.stream().map(f).filter(Objects::nonNull).filter(v -> !v.isBlank()).distinct().toList();
    }

    private static String list(Set<String> values) {
        if (values.isEmpty()) {
            return "none";
        }
        List<String> v = new ArrayList<>(values);
        return v.size() <= 3 ? String.join(", ", v) : String.join(", ", v.subList(0, 3)) + " and " + (v.size() - 3) + " more";
    }

    public static boolean isPrivate(String ip) {
        return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("127.")
                || ip.matches("^172\\.(1[6-9]|2\\d|3[01])\\..*");
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
