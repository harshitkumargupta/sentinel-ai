package com.sentinelai.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ai.AiProperties;
import com.sentinelai.ai.local.LocalAnalysisEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the underlying {@link LlmClient} from {@code sentinel.ai.provider}: {@code http} for a real
 * OpenAI-compatible endpoint, {@code fake} for the test stub, otherwise (the default, {@code local})
 * the offline {@link LocalLlmClient}. The chosen client is
 * always wrapped by {@link GuardedLlmClient}; the {@code ai.enabled} flag is enforced higher up, so
 * when AI is off this client is simply never called.
 */
@Slf4j
@Configuration
public class LlmConfig {

    @Bean
    @Qualifier("llmDelegate")
    public LlmClient llmDelegate(AiProperties props, ObjectMapper objectMapper, LocalAnalysisEngine localEngine) {
        String provider = props.getProvider() == null ? "" : props.getProvider().toLowerCase(java.util.Locale.ROOT);
        return switch (provider) {
            case "http" -> {
                log.info("AI provider: http (model={}, baseUrl={})", props.getModel(), props.getBaseUrl());
                yield new HttpLlmClient(props, objectMapper);
            }
            case "fake" -> {
                log.info("AI provider: fake (deterministic test stub)");
                yield new FakeLlmClient(objectMapper);
            }
            default -> {
                log.info("AI provider: local (offline rule/template engine, no API key)");
                yield new LocalLlmClient(localEngine, objectMapper);
            }
        };
    }
}
