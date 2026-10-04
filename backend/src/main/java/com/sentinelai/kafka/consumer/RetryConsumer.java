package com.sentinelai.kafka.consumer;

import com.sentinelai.kafka.KafkaProperties;
import com.sentinelai.kafka.PipelineHeaders;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Single-threaded consumer of {@code events.retry}. It applies an exponential backoff (sleeping on
 * its own partition only, never the main ones), re-dispatches the message to the original handler,
 * and on repeated failure re-routes it — back to retry while attempts remain, otherwise to the DLQ.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class RetryConsumer {

    private final Map<String, PipelineHandler> handlers;
    private final IdempotentExecutor executor;
    private final PipelineErrorRouter errorRouter;
    private final KafkaProperties props;
    private final MeterRegistry meters;

    public RetryConsumer(List<PipelineHandler> handlerBeans, IdempotentExecutor executor,
                         PipelineErrorRouter errorRouter, KafkaProperties props, MeterRegistry meters) {
        this.handlers = handlerBeans.stream()
                .collect(Collectors.toMap(PipelineHandler::name, Function.identity()));
        this.executor = executor;
        this.errorRouter = errorRouter;
        this.props = props;
        this.meters = meters;
    }

    @KafkaListener(id = "retry", topics = "${sentinel.kafka.topics.retry}",
            groupId = "${sentinel.kafka.groups.retry}", concurrency = "1")
    public void onRetry(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String handlerName = ConsumerSupport.header(record, PipelineHeaders.HANDLER, null);
        String messageId = ConsumerSupport.messageId(record);
        String originalTopic = ConsumerSupport.header(record, PipelineHeaders.ORIGINAL_TOPIC, record.topic());
        int attempt = parseInt(ConsumerSupport.header(record, PipelineHeaders.ATTEMPT, "1"));

        try {
            backoff(attempt);
            PipelineHandler handler = handlers.get(handlerName);
            if (handler == null) {
                // Unknown handler — nothing can process it; send straight to the DLQ.
                errorRouter.route(record.key(), record.value(), handlerName, messageId, originalTopic,
                        props.getMaxRetries(), new IllegalStateException("no handler: " + handlerName));
                return;
            }
            boolean fresh = executor.execute(handler.group(), messageId, () -> handler.handle(record));
            meters.counter("sentinel.kafka.consume", "group", handler.group(),
                    "outcome", fresh ? "ok-retry" : "duplicate").increment();
        } catch (Exception ex) {
            errorRouter.route(record.key(), record.value(), handlerName, messageId, originalTopic, attempt, ex);
        } finally {
            ack.acknowledge();
        }
    }

    private void backoff(int attempt) {
        long delay = Math.min(props.getMaxRetryBackoffMs(),
                (long) (props.getRetryBackoffMs() * Math.pow(2, Math.max(0, attempt - 1))));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
