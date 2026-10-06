package com.sentinelai.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ai.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the underlying {@link LlmClient} from {@code sentinel.ai.provider}: {@code http} for a real
 * OpenAI-compatible endpoint, otherwise the deterministic {@link FakeLlmClient}. The chosen client is
 * always wrapped by {@link GuardedLlmClient}; the {@code ai.enabled} flag is enforced higher up, so
 * when AI is off this client is simply never called.
 */
@Slf4j
@Configuration
public class LlmConfig {

    @Bean
    @Qualifier("llmDelegate")
    public LlmClient llmDelegate(AiProperties props, ObjectMapper objectMapper) {
        if ("http".equalsIgnoreCase(props.getProvider())) {
            log.info("AI provider: http (model={}, baseUrl={})", props.getModel(), props.getBaseUrl());
            return new HttpLlmClient(props, objectMapper);
        }
        log.info("AI provider: fake (deterministic)");
        return new FakeLlmClient(objectMapper);
    }
}
