package com.sentinelai.kafka.consumer;

import com.sentinelai.kafka.consumer.handler.AnalyticsHandler;
import com.sentinelai.kafka.consumer.handler.CorrelationHandler;
import com.sentinelai.kafka.consumer.handler.DetectionHandler;
import com.sentinelai.kafka.consumer.handler.NotificationHandler;
import com.sentinelai.kafka.consumer.handler.RawIngestHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Live listeners, one per pipeline stage. Each belongs to its own consumer group — set on the
 * {@code @KafkaListener}, not shared — so the stages scale and fail independently. All of them route
 * through {@link ConsumerSupport} for idempotency, manual commit and retry/DLQ handling.
 *
 * <p>Note {@code events.normalized} feeds two groups (detection and analytics); each sees every
 * message because the groups differ.
 */
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class PipelineListeners {

    private final ConsumerSupport support;
    private final RawIngestHandler rawIngest;
    private final DetectionHandler detection;
    private final AnalyticsHandler analytics;
    private final CorrelationHandler correlation;
    private final NotificationHandler notification;

    public PipelineListeners(ConsumerSupport support, RawIngestHandler rawIngest,
                             DetectionHandler detection, AnalyticsHandler analytics,
                             CorrelationHandler correlation, NotificationHandler notification) {
        this.support = support;
        this.rawIngest = rawIngest;
        this.detection = detection;
        this.analytics = analytics;
        this.correlation = correlation;
        this.notification = notification;
    }

    @KafkaListener(id = "raw-ingest", topics = "${sentinel.kafka.topics.raw}",
            groupId = "${sentinel.kafka.groups.raw-ingest}")
    public void onRaw(ConsumerRecord<String, String> record, Acknowledgment ack) {
        support.consume(rawIngest, record, ack);
    }

    @KafkaListener(id = "detection", topics = "${sentinel.kafka.topics.normalized}",
            groupId = "${sentinel.kafka.groups.detection}")
    public void onNormalizedForDetection(ConsumerRecord<String, String> record, Acknowledgment ack) {
        support.consume(detection, record, ack);
    }

    @KafkaListener(id = "analytics", topics = "${sentinel.kafka.topics.normalized}",
            groupId = "${sentinel.kafka.groups.analytics}")
    public void onNormalizedForAnalytics(ConsumerRecord<String, String> record, Acknowledgment ack) {
        support.consume(analytics, record, ack);
    }

    @KafkaListener(id = "correlation", topics = "${sentinel.kafka.topics.alerts}",
            groupId = "${sentinel.kafka.groups.correlation}")
    public void onAlert(ConsumerRecord<String, String> record, Acknowledgment ack) {
        support.consume(correlation, record, ack);
    }

    @KafkaListener(id = "notification", topics = "${sentinel.kafka.topics.incidents}",
            groupId = "${sentinel.kafka.groups.notification}")
    public void onIncidentUpdate(ConsumerRecord<String, String> record, Acknowledgment ack) {
        support.consume(notification, record, ack);
    }
}
