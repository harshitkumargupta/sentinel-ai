package com.sentinelai.kafka.consumer;

import com.sentinelai.kafka.KafkaEventPublisher;
import com.sentinelai.kafka.KafkaProperties;
import com.sentinelai.kafka.PipelineHeaders;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Routes a failed message onward: to {@code events.retry} with an incremented attempt while retries
 * remain, then to {@code events.dlq}. Because the main listeners acknowledge after routing, a poison
 * message is moved aside immediately and never blocks its partition.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class PipelineErrorRouter {

    private final KafkaEventPublisher publisher;
    private final KafkaProperties props;
    private final MeterRegistry meters;

    public PipelineErrorRouter(KafkaEventPublisher publisher, KafkaProperties props, MeterRegistry meters) {
        this.publisher = publisher;
        this.props = props;
        this.meters = meters;
    }

    /**
     * @param currentAttempt attempts already made (0 on the first failure from a live listener).
     */
    public void route(String key, String value, String handlerName, String messageId,
                       String originalTopic, int currentAttempt, Throwable ex) {
        int nextAttempt = currentAttempt + 1;
        boolean toDlq = nextAttempt > props.getMaxRetries();
        String target = toDlq ? props.getTopics().getDlq() : props.getTopics().getRetry();

        ProducerRecord<String, String> record = new ProducerRecord<>(target, key, value);
        Headers h = record.headers();
        add(h, PipelineHeaders.MESSAGE_ID, messageId);
        add(h, PipelineHeaders.HANDLER, handlerName);
        add(h, PipelineHeaders.ORIGINAL_TOPIC, originalTopic);
        add(h, PipelineHeaders.ATTEMPT, Integer.toString(nextAttempt));
        add(h, PipelineHeaders.ERROR, summarize(ex));
        publisher.send(record);

        meters.counter(toDlq ? "sentinel.kafka.dlq" : "sentinel.kafka.retry",
                "handler", handlerName == null ? "unknown" : handlerName).increment();
        if (toDlq) {
            log.error("Message {} ({}) dead-lettered after {} attempts: {}",
                    messageId, handlerName, currentAttempt, summarize(ex));
        } else {
            log.warn("Message {} ({}) routed to retry (attempt {}): {}",
                    messageId, handlerName, nextAttempt, summarize(ex));
        }
    }

    private static void add(Headers h, String name, String value) {
        if (value != null) {
            h.add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String summarize(Throwable ex) {
        if (ex == null) {
            return "unknown";
        }
        String msg = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
