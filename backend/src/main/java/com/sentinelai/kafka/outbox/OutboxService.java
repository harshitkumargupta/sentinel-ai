package com.sentinelai.kafka.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Writes outbox rows. Called from inside a domain transaction so the row commits atomically with
 * the aggregate change it describes; {@link OutboxRelay} publishes it afterwards.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    /** Enqueue a payload for publication to {@code topic}, keyed by {@code key}. */
    public OutboxMessage enqueue(String aggregateType, Long aggregateId, String topic, String key, Object payload) {
        String json;
        try {
            json = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialize outbox payload for " + topic, e);
        }
        return repository.save(OutboxMessage.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .topic(topic)
                .kafkaKey(key)
                .payload(json)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .build());
    }
}
