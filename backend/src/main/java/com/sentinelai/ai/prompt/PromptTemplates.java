package com.sentinelai.ai.prompt;

import com.sentinelai.ai.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads versioned prompt templates from {@code resources/prompts/<name>.<version>.txt} and performs
 * simple {@code {{placeholder}}} substitution. The active version comes from config and is recorded
 * on every analysis, so an analysis always traces back to the exact prompt that produced it.
 */
@Component
@RequiredArgsConstructor
public class PromptTemplates {

    private final AiProperties props;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String version() {
        return props.getTemplateVersion();
    }

    /** Load a template (e.g. name="investigation.system") for the active version. */
    public String load(String name) {
        return cache.computeIfAbsent(name + "." + version(), this::read);
    }

    /** Load and substitute {@code {{key}}} placeholders. */
    public String render(String name, Map<String, String> vars) {
        String out = load(name);
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private String read(String key) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("prompts/" + key + ".txt").getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing prompt template: prompts/" + key + ".txt", e);
        }
    }
}
