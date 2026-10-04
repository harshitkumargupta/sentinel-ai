package com.sentinelai.kafka.consumer.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.alert.domain.Alert;
import com.sentinelai.detection.engine.SynchronousEventProcessor;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.kafka.KafkaProperties;
import com.sentinelai.kafka.consumer.PipelineHandler;
import com.sentinelai.kafka.message.AlertMessage;
import com.sentinelai.kafka.message.NormalizedEventMessage;
import com.sentinelai.kafka.outbox.OutboxService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code events.normalized → rules → alerts}. Runs the detection rules against the persisted event
 * and enqueues each resulting alert to the {@code alerts} topic via the outbox, so the alert row and
 * its outbox row commit together (no lost or duplicated alerts on retry).
 */
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class DetectionHandler implements PipelineHandler {

    private final ObjectMapper objectMapper;
    private final SecurityEventRepository eventRepository;
    private final SynchronousEventProcessor processor;
    private final OutboxService outboxService;
    private final KafkaProperties props;

    public DetectionHandler(ObjectMapper objectMapper, SecurityEventRepository eventRepository,
                            SynchronousEventProcessor processor, OutboxService outboxService,
                            KafkaProperties props) {
        this.objectMapper = objectMapper;
        this.eventRepository = eventRepository;
        this.processor = processor;
        this.outboxService = outboxService;
        this.props = props;
    }

    @Override
    public String name() {
        return "detection";
    }

    @Override
    public String group() {
        return props.getGroups().getDetection();
    }

    @Override
    public void handle(ConsumerRecord<String, String> record) throws Exception {
        NormalizedEventMessage msg = objectMapper.readValue(record.value(), NormalizedEventMessage.class);
        SecurityEvent event = eventRepository.findById(msg.eventId())
                .orElseThrow(() -> new IllegalStateException("event not found: " + msg.eventId()));

        for (Alert alert : processor.runRules(event)) {
            String key = alert.getEntityKey() != null ? alert.getEntityKey() : msg.entityKey();
            outboxService.enqueue("alert", alert.getId(), props.getTopics().getAlerts(), key,
                    new AlertMessage(alert.getId(), event.getOrg().getId(), alert.getEntityKey(),
                            alert.getSeverity().name()));
        }
    }
}
