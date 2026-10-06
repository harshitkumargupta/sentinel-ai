package com.sentinelai.kafka.consumer.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.kafka.KafkaProperties;
import com.sentinelai.kafka.consumer.PipelineHandler;
import com.sentinelai.kafka.message.IncidentUpdateMessage;
import com.sentinelai.notification.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code incidents.updates → notifications}. Raises admin notifications for HIGH/CRITICAL incidents.
 * Idempotent per {@code (group, messageId)}, so a redelivery never double-notifies.
 */
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class NotificationHandler implements PipelineHandler {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final KafkaProperties props;

    public NotificationHandler(ObjectMapper objectMapper, NotificationService notificationService,
                               KafkaProperties props) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.props = props;
    }

    @Override
    public String name() {
        return "notification";
    }

    @Override
    public String group() {
        return props.getGroups().getNotification();
    }

    @Override
    public void handle(ConsumerRecord<String, String> record) throws Exception {
        IncidentUpdateMessage msg = objectMapper.readValue(record.value(), IncidentUpdateMessage.class);
        Severity severity = Severity.valueOf(msg.severity());
        if (severity.ordinal() >= Severity.HIGH.ordinal()) {
            notificationService.notifyAdminsOfEscalation(msg.incidentId(), severity, msg.riskScore());
        }
    }
}
