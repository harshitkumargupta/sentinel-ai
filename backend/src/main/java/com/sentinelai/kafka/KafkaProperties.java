package com.sentinelai.kafka;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kafka pipeline configuration. Disabled by default — when {@code sentinel.kafka.enabled=false}
 * no producer/consumer beans are created and ingestion keeps using the synchronous in-process
 * detection path. Everything here is overridable per profile and via env var.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.kafka")
public class KafkaProperties {

    /** Master feature flag for the whole Kafka pipeline. */
    private boolean enabled = false;

    @NotBlank
    private String bootstrapServers = "localhost:9092";

    /** Partitions for the business topics (events.raw/normalized/alerts/incidents.updates). */
    @Min(1)
    private int partitions = 3;

    /** Replication factor for created topics (1 for single-node dev). */
    @Min(1)
    private short replicationFactor = 1;

    /** Listener container concurrency (consumer threads per @KafkaListener). */
    @Min(1)
    private int concurrency = 3;

    /** Max retry attempts (via the retry topic) before a message is dead-lettered. */
    @Min(0)
    private int maxRetries = 3;

    /** Base backoff for the retry topic; the delay is {@code backoffMs * 2^(attempt-1)}. */
    @Min(1)
    private long retryBackoffMs = 500;

    /** Cap on the exponential retry backoff. */
    @Min(1)
    private long maxRetryBackoffMs = 30_000;

    /** Outbox relay poll interval. */
    @Min(50)
    private long relayIntervalMs = 500;

    /** Max outbox rows published per relay tick. */
    @Min(1)
    private int relayBatchSize = 200;

    private final Topics topics = new Topics();
    private final Groups groups = new Groups();

    /** Topic names — one entity's events stay ordered per partition because messages are keyed by entity_key. */
    @Getter
    @Setter
    public static class Topics {
        @NotBlank private String raw = "events.raw";
        @NotBlank private String normalized = "events.normalized";
        @NotBlank private String alerts = "alerts";
        @NotBlank private String incidents = "incidents.updates";
        @NotBlank private String retry = "events.retry";
        @NotBlank private String dlq = "events.dlq";
    }

    /** Consumer group ids — separate groups so each stage scales and fails independently. */
    @Getter
    @Setter
    public static class Groups {
        @NotBlank private String rawIngest = "sentinel-raw-ingest";
        @NotBlank private String detection = "sentinel-detection";
        @NotBlank private String correlation = "sentinel-correlation";
        @NotBlank private String analytics = "sentinel-analytics";
        @NotBlank private String notification = "sentinel-notification";
        @NotBlank private String retry = "sentinel-retry";
        @NotBlank private String dlq = "sentinel-dlq";
    }
}
