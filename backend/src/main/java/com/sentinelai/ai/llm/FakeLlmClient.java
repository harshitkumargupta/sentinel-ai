package com.sentinelai.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinelai.ai.context.IncidentContext;
import com.sentinelai.ai.security.PromptSanitizer;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic {@link LlmClient} for tests, CI and the dev default. It never calls a network and
 * never "follows" instructions — it mechanically derives a schema-valid response from the structured
 * data in the prompt, so pipeline tests are stable and an injection string in the data can never
 * change the output.
 */
@RequiredArgsConstructor
public class FakeLlmClient implements LlmClient {

    private static final Pattern IPV4 =
            Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b");

    private final ObjectMapper objectMapper;

    @Override
    public LlmResponse complete(LlmRequest request) {
        String content = switch (request.purpose() == null ? "" : request.purpose()) {
            case "investigation" -> investigation(request.userPrompt());
            case "nl-search" -> nlSearch(request.userPrompt());
            case "admin-risk" -> adminRisk(request.userPrompt());
            default -> "{}";
        };
        int promptTokens = Math.max(1, (request.systemPrompt().length() + request.userPrompt().length()) / 4);
        int completionTokens = Math.max(1, content.length() / 4);
        return new LlmResponse(content, "fake-model", promptTokens, completionTokens, 3);
    }

    private String investigation(String userPrompt) {
        IncidentContext ctx = extractContext(userPrompt);
        ObjectNode root = objectMapper.createObjectNode();
        if (ctx == null || ctx.eventIds() == null || ctx.eventIds().isEmpty()) {
            root.put("summary", "No events available to analyze.");
            root.put("confidence", 0.1);
            root.set("hypotheses", objectMapper.createArrayNode());
            root.set("recommendations", objectMapper.createArrayNode());
            root.set("claims", objectMapper.createArrayNode());
            return root.toString();
        }
        String eType = ctx.events().isEmpty() ? "activity" : String.valueOf(ctx.events().get(0).type());
        String entityType = ctx.entity() == null ? "unknown" : ctx.entity().type();
        String entityValue = ctx.entity() == null ? null : ctx.entity().value();

        root.put("summary", "Observed %d %s event(s) for %s '%s' (risk %s, severity %s)."
                .formatted(ctx.eventIds().size(), eType, entityType, entityValue,
                        String.valueOf(ctx.riskScore()), ctx.severity()));
        root.put("confidence", 0.62);

        ArrayNode hyp = root.putArray("hypotheses");
        hyp.add("Repeated %s against %s is consistent with an active attack rather than benign use."
                .formatted(eType, entityValue));

        ArrayNode recs = root.putArray("recommendations");
        if ("ip".equals(entityType) && entityValue != null) {
            recs.addObject().put("action", "block_ip").put("target", entityValue)
                    .put("reason", "Source IP is the common factor across the cited events.");
        } else if ("user".equals(entityType) && entityValue != null) {
            recs.addObject().put("action", "disable_user").put("target", entityValue)
                    .put("reason", "Account is the target of the cited events; disable pending review.");
        }
        if (entityValue != null) {
            recs.addObject().put("action", "monitor").put("target", entityValue)
                    .put("reason", "Continue monitoring for further activity.");
        }

        ArrayNode claims = root.putArray("claims");
        ObjectNode claim = claims.addObject();
        claim.put("text", "%d events of type %s were recorded for %s."
                .formatted(ctx.eventIds().size(), eType, entityValue));
        ArrayNode ev = claim.putArray("evidenceEventIds");
        ctx.eventIds().stream().limit(10).forEach(ev::add);
        return root.toString();
    }

    private String nlSearch(String userPrompt) {
        String q = userPrompt.toLowerCase(Locale.ROOT);
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode f = root.putObject("filters");

        Matcher ip = IPV4.matcher(userPrompt);
        if (ip.find()) {
            f.put("ip", ip.group());
        }
        for (String sev : List.of("critical", "high", "medium", "low")) {
            if (q.contains(sev)) {
                f.put("severity", sev.toUpperCase(Locale.ROOT));
                break;
            }
        }
        String type = mapType(q);
        if (type != null) {
            f.put("type", type);
        }
        Matcher user = Pattern.compile("user(?:name)?\\s+([a-zA-Z0-9._-]{2,})").matcher(q);
        if (user.find()) {
            f.put("user", user.group(1));
        }
        Matcher days = Pattern.compile("last\\s+(\\d+)\\s+day").matcher(q);
        if (days.find()) {
            root.put("rangeDays", Integer.parseInt(days.group(1)));
        } else if (q.contains("last 24 hours") || q.contains("today")) {
            root.put("rangeDays", 1);
        } else if (q.contains("last week")) {
            root.put("rangeDays", 7);
        }
        Matcher cc = Pattern.compile("(?:country|from)\\s+([a-zA-Z]{2})\\b").matcher(q);
        if (cc.find()) {
            f.put("country", cc.group(1).toUpperCase(Locale.ROOT));
        }
        return root.toString();
    }

    private String mapType(String q) {
        if (q.contains("brute")) return "BRUTE_FORCE";
        if (q.contains("failed login")) return "FAILED_LOGIN";
        if (q.contains("honeytoken") || q.contains("honey token")) return "HONEYTOKEN_ACCESS";
        if (q.contains("api abuse")) return "API_ABUSE";
        if (q.contains("suspicious")) return "SUSPICIOUS_LOGIN";
        if (q.contains("abnormal")) return "ABNORMAL_ACCESS";
        return null;
    }

    private String adminRisk(String userPrompt) {
        return objectMapper.createObjectNode()
                .put("summary", "Deterministic review of the admin action based on the cited risk factors.")
                .toString();
    }

    private IncidentContext extractContext(String userPrompt) {
        int start = userPrompt.indexOf(PromptSanitizer.DATA_OPEN);
        int end = userPrompt.indexOf(PromptSanitizer.DATA_CLOSE);
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        String json = userPrompt.substring(start + PromptSanitizer.DATA_OPEN.length(), end).trim();
        try {
            return objectMapper.readValue(json, IncidentContext.class);
        } catch (Exception e) {
            return null;
        }
    }
}
