package com.sentinelai.kafka.consumer.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.alert.domain.Alert;
import com.sentinelai.alert.repository.AlertRepository;
import com.sentinelai.incident.correlation.CorrelationService;
import com.sentinelai.incident.correlation.CorrelationService.CorrelationOutcome;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.kafka.KafkaProperties;
import com.sentinelai.kafka.consumer.PipelineHandler;
import com.sentinelai.kafka.message.AlertMessage;
import com.sentinelai.kafka.message.IncidentUpdateMessage;
import com.sentinelai.kafka.outbox.OutboxService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code alerts → incidents}. Correlates the alert into an incident (notifications deferred) and,
 * when the incident escalates, enqueues an {@code incidents.updates} message via the outbox for the
 * notification consumer.
 */
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class CorrelationHandler implements PipelineHandler {

    private final ObjectMapper objectMapper;
    private final AlertRepository alertRepository;
    private final CorrelationService correlationService;
    private final OutboxService outboxService;
    private final KafkaProperties props;

    public CorrelationHandler(ObjectMapper objectMapper, AlertRepository alertRepository,
                              CorrelationService correlationService, OutboxService outboxService,
                              KafkaProperties props) {
        this.objectMapper = objectMapper;
        this.alertRepository = alertRepository;
        this.correlationService = correlationService;
        this.outboxService = outboxService;
        this.props = props;
    }

    @Override
    public String name() {
        return "correlation";
    }

    @Override
    public String group() {
        return props.getGroups().getCorrelation();
    }

    @Override
    public void handle(ConsumerRecord<String, String> record) throws Exception {
        AlertMessage msg = objectMapper.readValue(record.value(), AlertMessage.class);
        Alert alert = alertRepository.findById(msg.alertId())
                .orElseThrow(() -> new IllegalStateException("alert not found: " + msg.alertId()));

        CorrelationOutcome outcome = correlationService.correlateForPipeline(alert);
        Incident incident = outcome.incident();
        if (incident != null && outcome.escalated()) {
            outboxService.enqueue("incident", incident.getId(), props.getTopics().getIncidents(),
                    msg.entityKey(),
                    new IncidentUpdateMessage(incident.getId(), msg.orgId(), msg.entityKey(),
                            incident.getSeverity().name(), incident.getRiskScore(), true));
        }
    }
}
