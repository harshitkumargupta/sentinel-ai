package com.sentinelai.ai.security;

import com.sentinelai.ai.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Masks PII (emails, optionally IPs) in text before it is sent to the model, per config. */
@Component
@RequiredArgsConstructor
public class PiiRedactor {

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern IPV4 =
            Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b");

    private final AiProperties props;

    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        if (props.getRedaction().isMaskEmails()) {
            out = EMAIL.matcher(out).replaceAll("[email]");
        }
        if (props.getRedaction().isMaskIps()) {
            out = IPV4.matcher(out).replaceAll("[ip]");
        }
        return out;
    }
}
