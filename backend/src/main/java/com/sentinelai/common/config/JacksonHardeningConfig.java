package com.sentinelai.common.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bounds untrusted JSON parsing so a hostile or runaway payload can't exhaust memory/stack: caps
 * nesting depth, individual string/number/name length, and overall document size. Applied to every
 * Jackson {@code ObjectMapper} Spring builds (request bodies included).
 */
@Configuration
public class JacksonHardeningConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer jsonStreamReadConstraints() {
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxNestingDepth(64)
                .maxStringLength(200_000)
                .maxNumberLength(1_000)
                .maxNameLength(1_000)
                .maxDocumentLength(2_000_000) // ~2MB of JSON
                .build();
        return builder -> builder.postConfigurer(mapper ->
                mapper.getFactory().setStreamReadConstraints(constraints));
    }
}
