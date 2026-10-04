package com.sentinelai.kafka.consumer.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.baseline.BehavioralBaselineService;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.kafka.KafkaProperties;
import com.sentinelai.kafka.consumer.PipelineHandler;
import com.sentinelai.kafka.message.NormalizedEventMessage;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code events.normalized → counters + baselines}. A separate consumer group from detection, so
 * analytics can lag, fail or be scaled independently without affecting detection.
 */
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class AnalyticsHandler implements PipelineHandler {

    private final ObjectMapper objectMapper;
    private final SecurityEventRepository eventRepository;
    private final BehavioralBaselineService baselineService;
    private final MeterRegistry meters;
    private final KafkaProperties props;

    public AnalyticsHandler(ObjectMapper objectMapper, SecurityEventRepository eventRepository,
                            BehavioralBaselineService baselineService, MeterRegistry meters,
                            KafkaProperties props) {
        this.objectMapper = objectMapper;
        this.eventRepository = eventRepository;
        this.baselineService = baselineService;
        this.meters = meters;
        this.props = props;
    }

    @Override
    public String name() {
        return "analytics";
    }

    @Override
    public String group() {
        return props.getGroups().getAnalytics();
    }

    @Override
    public void handle(ConsumerRecord<String, String> record) throws Exception {
        NormalizedEventMessage msg = objectMapper.readValue(record.value(), NormalizedEventMessage.class);
        SecurityEvent event = eventRepository.findById(msg.eventId())
                .orElseThrow(() -> new IllegalStateException("event not found: " + msg.eventId()));

        meters.counter("sentinel.analytics.events",
                "type", event.getEventType().name(),
                "severity", event.getSeverity().name()).increment();

        if (event.getEntityKey() != null) {
            baselineService.observe(event.getEntityKey(), "event_count", 1.0);
        }
    }
}
