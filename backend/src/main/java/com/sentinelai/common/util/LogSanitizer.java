package com.sentinelai.common.util;

/**
 * Strips control characters (notably CR/LF) from values before they are written to logs, preventing
 * log-forging/injection where attacker-controlled input could splice in forged log lines. Use this
 * on any user-supplied value (username, header, free text) that ends up in a log message.
 */
public final class LogSanitizer {

    private static final int MAX = 256;

    private LogSanitizer() {
    }

    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.length() > MAX ? value.substring(0, MAX) + "…" : value;
        StringBuilder sb = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            // Drop CR, LF, tab and other C0/C1 control chars; keep normal printable text.
            sb.append(Character.isISOControl(c) ? '_' : c);
        }
        return sb.toString();
    }
}
