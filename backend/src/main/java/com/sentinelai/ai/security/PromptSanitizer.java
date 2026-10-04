package com.sentinelai.ai.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Prepares untrusted, attacker-controlled text for inclusion in a prompt: strips control characters,
 * neutralizes instruction-like patterns, truncates, and wraps the whole payload in clearly delimited
 * data markers. The model is told everything between the markers is untrusted data, never commands.
 */
@Component
public class PromptSanitizer {

    /** Delimiters around the untrusted data block (the model is told these fence off data). */
    public static final String DATA_OPEN = "<<<UNTRUSTED_EVENT_DATA";
    public static final String DATA_CLOSE = "UNTRUSTED_EVENT_DATA>>>";

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
    private static final Pattern INSTRUCTION_LIKE = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+|the\\s+)?(previous|prior|above)\\s+instructions"
                    + "|disregard\\s+(the\\s+|all\\s+)?(above|previous|instructions)"
                    + "|new\\s+instructions?\\s*:"
                    + "|system\\s+prompt"
                    + "|you\\s+are\\s+now"
                    + "|</?(system|assistant|user)>)");

    /** Strip control chars, neutralize instruction patterns, and truncate a single field value. */
    public String sanitizeField(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        String out = CONTROL_CHARS.matcher(value).replaceAll(" ");
        out = INSTRUCTION_LIKE.matcher(out).replaceAll("[filtered-instruction]");
        // Neutralize our own delimiters if an attacker tries to inject them.
        out = out.replace(DATA_OPEN, "[x]").replace(DATA_CLOSE, "[x]");
        if (out.length() > maxChars) {
            out = out.substring(0, maxChars) + "…";
        }
        return out;
    }

    /** Wrap an already-assembled (sanitized) data payload in the untrusted-data fence. */
    public String wrapData(String payload) {
        return DATA_OPEN + "\n" + payload + "\n" + DATA_CLOSE;
    }
}
