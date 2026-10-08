package com.sentinelai.ai.assistant;

import com.sentinelai.ai.context.IncidentContext;
import com.sentinelai.ai.local.LocalAnalysisEngine;
import com.sentinelai.ai.pipeline.AnalysisOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Offline assistant: answers each {@link AskIntent} from the {@link LocalAnalysisEngine} report. */
@Component
@RequiredArgsConstructor
public class LocalIncidentAssistant implements IncidentAssistant {

    private final LocalAnalysisEngine engine;

    @Override
    public AssistantAnswer answer(IncidentContext ctx, AskIntent intent, String question) {
        if (intent == null) {
            return reply(null, question, "I can answer these questions about this incident offline: "
                            + Arrays.stream(AskIntent.values()).map(AskIntent::label).collect(Collectors.joining(" · ")),
                    List.of(), List.of());
        }
        AnalysisOutput out = engine.analyze(ctx);
        AnalysisOutput.Report r = out.report();
        List<Long> allIds = ctx.eventIds() == null ? List.of() : ctx.eventIds().stream().limit(20).toList();
        return switch (intent) {
            case WHAT_HAPPENED -> reply(intent, question, r.whatHappened(),
                    r.timeline().stream().map(t -> time(t.at()) + " — " + t.description()).toList(), allIds);
            case WHY_RISKY -> reply(intent, question, r.severityReasoning(), out.hypotheses(), allIds);
            case WHAT_TO_DO -> reply(intent, question,
                    "Recommended response, in order. Each action runs through dry-run → approve → execute and can be rolled back.",
                    r.nextSteps(), List.of());
            case WHICH_IPS -> ips(intent, question, ctx);
            case WHICH_MITRE -> reply(intent, question,
                    r.mitre().isEmpty() ? "No MITRE ATT&CK techniques are tagged on this incident's alerts."
                            : "%d ATT&CK technique(s) are mapped from the alerts in this incident.".formatted(r.mitre().size()),
                    r.mitre().stream().map(m -> m.id() + " — " + m.name() + " (" + m.tactic() + ")").toList(),
                    List.of());
        };
    }

    private AssistantAnswer ips(AskIntent intent, String question, IncidentContext ctx) {
        Map<String, List<IncidentContext.EventSummary>> byIp = new LinkedHashMap<>();
        ctx.events().stream().filter(e -> e.sourceIp() != null)
                .forEach(e -> byIp.computeIfAbsent(e.sourceIp(), k -> new java.util.ArrayList<>()).add(e));
        if (byIp.isEmpty()) {
            return reply(intent, question, "No source IPs are recorded on this incident's events.", List.of(), List.of());
        }
        List<String> bullets = byIp.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(15)
                .map(e -> {
                    String country = e.getValue().stream().map(IncidentContext.EventSummary::geoCountry)
                            .filter(Objects::nonNull).findFirst().orElse("unknown country");
                    String types = e.getValue().stream().map(IncidentContext.EventSummary::type)
                            .distinct().collect(Collectors.joining(", "));
                    String scope = LocalAnalysisEngine.isPrivate(e.getKey()) ? "internal" : "external";
                    return "%s (%s, %s) — %d event(s): %s".formatted(e.getKey(), country, scope, e.getValue().size(), types);
                })
                .toList();
        List<Long> ids = byIp.values().stream().flatMap(List::stream)
                .map(IncidentContext.EventSummary::id).limit(20).toList();
        return reply(intent, question, "%d distinct source IP(s) are involved.".formatted(byIp.size()), bullets, ids);
    }

    private static String time(String iso) {
        return iso == null ? "—" : iso.replace('T', ' ').replaceAll("\\.\\d+Z$", "Z");
    }

    private AssistantAnswer reply(AskIntent intent, String question, String answer, List<String> bullets, List<Long> ids) {
        return new AssistantAnswer(intent == null ? null : intent.name(),
                question != null ? question : intent == null ? null : intent.label(),
                answer, bullets == null ? List.of() : bullets, ids, LocalAnalysisEngine.MODEL_NAME, true);
    }
}
