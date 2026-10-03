package com.sentinelai.ingestion;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Ingestion limits — overridable per profile / env var. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.ingestion")
public class IngestionProperties {

    @Min(1)
    private int maxBatchSize = 1000;

    /** Max serialized raw payload size per event (bytes). */
    @Min(256)
    private int maxPayloadBytes = 16384;
}
