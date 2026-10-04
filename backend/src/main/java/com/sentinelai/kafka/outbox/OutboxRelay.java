package com.sentinelai.kafka.outbox;

import com.sentinelai.kafka.EventPublisher;
import com.sentinelai.kafka.KafkaProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Publishes PENDING outbox rows to Kafka and marks them SENT. Runs only when the Kafka pipeline is
 * enabled. If the broker is unreachable, rows stay PENDING and accumulate (visible as the outbox
 * backlog metric) and are published once the broker returns — so a crash or outage between the DB
 * commit and the publish never loses an event. The message id is the row id, so a duplicate publish
 * after an ack timeout deduplicates to the same logical message downstream.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class OutboxRelay {

    private final OutboxRepository repository;
    private final EventPublisher publisher;
    private final KafkaProperties props;
    private final MeterRegistry meters;
    private final Clock clock;

    public OutboxRelay(OutboxRepository repository, EventPublisher publisher, KafkaProperties props,
                       MeterRegistry meters, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.props = props;
        this.meters = meters;
        this.clock = clock;
        meters.gauge("sentinel.kafka.outbox.backlog", this, OutboxRelay::backlog);
    }

    double backlog() {
        return repository.backlog();
    }

    @Scheduled(fixedDelayString = "${sentinel.kafka.relay-interval-ms:500}")
    @Transactional
    public void relay() {
        List<OutboxMessage> batch = repository.findByStatusOrderByIdAsc(
                OutboxStatus.PENDING, PageRequest.of(0, props.getRelayBatchSize()));
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxMessage row : batch) {
            boolean ok = publisher.publish(row.getTopic(), row.getKafkaKey(), row.getPayload(),
                    "ob-" + row.getId());
            row.setAttempts(row.getAttempts() + 1);
            if (ok) {
                row.setStatus(OutboxStatus.SENT);
                row.setPublishedAt(Instant.now(clock));
                meters.counter("sentinel.kafka.outbox", "outcome", "sent").increment();
            } else {
                // Leave PENDING to retry on the next tick; the broker is likely down.
                meters.counter("sentinel.kafka.outbox", "outcome", "deferred").increment();
                log.warn("Outbox relay could not publish row {} to {} (attempt {}); will retry",
                        row.getId(), row.getTopic(), row.getAttempts());
                break; // stop the batch — no point hammering a down broker this tick
            }
        }
    }
}
