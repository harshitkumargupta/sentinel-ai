package com.sentinelai.kafka.consumer.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ingestion.IngestionService;
import com.sentinelai.kafka.KafkaProperties;
import com.sentinelai.kafka.consumer.PipelineHandler;
import com.sentinelai.kafka.message.RawIngestMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code events.raw → normalize + persist}. Entry point for external collectors and the burst
 * simulator: normalizes and persists the raw payload (writing the outbox row), converging on the
 * same path as the REST ingest endpoint.
 */
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class RawIngestHandler implements PipelineHandler {

    private final ObjectMapper objectMapper;
    private final IngestionService ingestionService;
    private final KafkaProperties props;

    public RawIngestHandler(ObjectMapper objectMapper, IngestionService ingestionService,
                            KafkaProperties props) {
        this.objectMapper = objectMapper;
        this.ingestionService = ingestionService;
        this.props = props;
    }

    @Override
    public String name() {
        return "raw-ingest";
    }

    @Override
    public String group() {
        return props.getGroups().getRawIngest();
    }

    @Override
    public void handle(ConsumerRecord<String, String> record) throws Exception {
        RawIngestMessage msg = objectMapper.readValue(record.value(), RawIngestMessage.class);
        ingestionService.ingest(msg.orgId(), msg.siteId(), msg.sourceType(), msg.payload(),
                msg.clientEventId());
    }
}
