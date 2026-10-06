package com.sentinelai.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.kafka.PipelineHeaders;
import com.sentinelai.kafka.dlq.DlqMessage;
import com.sentinelai.kafka.dlq.DlqMessageRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists dead letters from {@code events.dlq} into {@code dlq_messages}, so the DLQ admin API can
 * list and replay them without touching the broker. Always acknowledges — a failure to persist is
 * logged rather than retried, so the DLQ partition never stalls.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class DlqConsumer {

    private final DlqMessageRepository repository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;

    public DlqConsumer(DlqMessageRepository repository, ObjectMapper objectMapper, MeterRegistry meters) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.meters = meters;
    }

    @KafkaListener(id = "dlq", topics = "${sentinel.kafka.topics.dlq}",
            groupId = "${sentinel.kafka.groups.dlq}", concurrency = "1")
    public void onDlq(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            repository.save(DlqMessage.builder()
                    .originalTopic(ConsumerSupport.header(record, PipelineHeaders.ORIGINAL_TOPIC, record.topic()))
                    .handler(ConsumerSupport.header(record, PipelineHeaders.HANDLER, null))
                    .messageId(ConsumerSupport.header(record, PipelineHeaders.MESSAGE_ID, null))
                    .kafkaKey(record.key())
                    .payload(record.value())
                    .attempts(parseInt(ConsumerSupport.header(record, PipelineHeaders.ATTEMPT, "0")))
                    .error(ConsumerSupport.header(record, PipelineHeaders.ERROR, null))
                    .headers(headersJson(record))
                    .status(DlqMessage.Status.DEAD)
                    .build());
            meters.counter("sentinel.kafka.dlq.persisted").increment();
        } catch (Exception ex) {
            log.error("Failed to persist dead letter from {}: {}", record.topic(), ex.toString());
        } finally {
            ack.acknowledge();
        }
    }

    private String headersJson(ConsumerRecord<String, String> record) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Header h : record.headers()) {
            map.put(h.key(), h.value() == null ? null : new String(h.value(), StandardCharsets.UTF_8));
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
