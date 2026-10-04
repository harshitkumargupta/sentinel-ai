package com.sentinelai.kafka.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Consumer-side idempotency. Because messages are keyed by entity_key, the same {@code messageId}
 * is only ever handled by one thread within a group, so a seen-check followed by a marker insert
 * (both inside the handler's transaction) is sufficient: a redelivery sees the marker and is a
 * no-op, and if the handler's transaction rolls back the marker rolls back with it.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedMessageRepository repository;

    /** True if this group has already processed this message id. */
    public boolean alreadyProcessed(String consumerGroup, String messageId) {
        return repository.existsById(new ProcessedMessage.Key(consumerGroup, messageId));
    }

    /** Record that this group has processed this message id (within the caller's transaction). */
    public void markProcessed(String consumerGroup, String messageId) {
        repository.save(new ProcessedMessage(consumerGroup, messageId));
    }
}
