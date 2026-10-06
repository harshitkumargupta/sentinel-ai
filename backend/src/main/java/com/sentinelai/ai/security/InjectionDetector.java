package com.sentinelai.ai.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects prompt-injection attempts in attacker-controlled text (log fields, user agents, payloads).
 * Matches are used to flag the incident and raise a security event; they are informational only —
 * the real defense is that untrusted data is delimited, the model has no tools, and the output is
 * schema-validated against the evidence regardless of what the data says.
 */
@Component
public class InjectionDetector {

    private static final Pattern[] PATTERNS = {
            Pattern.compile("ignore\\s+(all\\s+|the\\s+)?(previous|prior|above)\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(the\\s+|all\\s+)?(above|previous|instructions|rules)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget\\s+(everything|your\\s+instructions|the\\s+above)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("new\\s+instructions?\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal\\s+(your\\s+)?(prompt|instructions|system)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(print|output|repeat)\\s+(your\\s+)?(prompt|instructions)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(exfiltrate|execute|run\\s+the\\s+following|eval\\()", Pattern.CASE_INSENSITIVE),
            Pattern.compile("</?(system|assistant|user)>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bassistant\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("override\\s+(the\\s+)?(rules|guardrails|policy)", Pattern.CASE_INSENSITIVE),
    };

    /** Returns the matched snippets (empty if none). */
    public List<String> detect(String text) {
        List<String> hits = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return hits;
        }
        for (Pattern p : PATTERNS) {
            var m = p.matcher(text);
            if (m.find()) {
                hits.add(m.group());
            }
        }
        return hits;
    }

    public boolean isInjection(String text) {
        return !detect(text).isEmpty();
    }
}
