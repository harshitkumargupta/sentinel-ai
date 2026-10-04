package com.sentinelai.kafka.consumer;

import com.sentinelai.kafka.PipelineHeaders;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Shared consumer plumbing: run the handler once (idempotently, transactionally), commit the offset
 * only on success, and on failure route the message to retry/DLQ and still commit so the partition
 * keeps moving. Also records processing-time and outcome metrics per handler/group.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class ConsumerSupport {

    private final IdempotentExecutor executor;
    private final PipelineErrorRouter errorRouter;
    private final MeterRegistry meters;

    public ConsumerSupport(IdempotentExecutor executor, PipelineErrorRouter errorRouter, MeterRegistry meters) {
        this.executor = executor;
        this.errorRouter = errorRouter;
        this.meters = meters;
    }

    /** Dispatch a live-listener record through a handler, then acknowledge. */
    public void consume(PipelineHandler handler, ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = messageId(record);
        log.debug("consume handler={} group={} topic={} partition={} offset={} msgId={}",
                handler.name(), handler.group(), record.topic(), record.partition(), record.offset(), messageId);
        Timer.Sample sample = Timer.start(meters);
        try {
            boolean fresh = executor.execute(handler.group(), messageId,
                    () -> handler.handle(record));
            meters.counter("sentinel.kafka.consume", "group", handler.group(),
                    "outcome", fresh ? "ok" : "duplicate").increment();
        } catch (Exception ex) {
            String orig = header(record, PipelineHeaders.ORIGINAL_TOPIC, record.topic());
            errorRouter.route(record.key(), record.value(), handler.name(), messageId, orig, 0, ex);
        } finally {
            sample.stop(meters.timer("sentinel.kafka.process.time", "handler", handler.name()));
            ack.acknowledge();
        }
    }

    /** The stable message id, falling back to topic-partition-offset if the header is absent. */
    public static String messageId(ConsumerRecord<String, String> record) {
        String id = header(record, PipelineHeaders.MESSAGE_ID, null);
        return id != null ? id : record.topic() + "-" + record.partition() + "-" + record.offset();
    }

    public static String header(ConsumerRecord<String, String> record, String name, String fallback) {
        Header h = record.headers().lastHeader(name);
        return h == null ? fallback : new String(h.value(), StandardCharsets.UTF_8);
    }
}
