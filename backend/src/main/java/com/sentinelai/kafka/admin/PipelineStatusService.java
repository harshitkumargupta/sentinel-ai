package com.sentinelai.kafka.admin;

import com.sentinelai.kafka.admin.KafkaAdminDtos.ListenerStatus;
import com.sentinelai.kafka.admin.KafkaAdminDtos.PipelineStatus;
import com.sentinelai.kafka.dlq.DlqMessage;
import com.sentinelai.kafka.dlq.DlqMessageRepository;
import com.sentinelai.kafka.idempotency.ProcessedMessageRepository;
import com.sentinelai.kafka.outbox.OutboxRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregates the one-glance pipeline health: lag, DLQ size, outbox backlog, listener states. */
@Service
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class PipelineStatusService {

    private final KafkaLagService lagService;
    private final OutboxRepository outboxRepository;
    private final DlqMessageRepository dlqRepository;
    private final ProcessedMessageRepository processedRepository;
    private final KafkaListenerEndpointRegistry registry;

    public PipelineStatusService(KafkaLagService lagService, OutboxRepository outboxRepository,
                                 DlqMessageRepository dlqRepository,
                                 ProcessedMessageRepository processedRepository,
                                 KafkaListenerEndpointRegistry registry) {
        this.lagService = lagService;
        this.outboxRepository = outboxRepository;
        this.dlqRepository = dlqRepository;
        this.processedRepository = processedRepository;
        this.registry = registry;
    }

    public PipelineStatus status() {
        List<ListenerStatus> listeners = new ArrayList<>();
        for (String id : registry.getListenerContainerIds()) {
            MessageListenerContainer c = registry.getListenerContainer(id);
            listeners.add(new ListenerStatus(id,
                    c != null && c.isRunning(),
                    c != null && c.isContainerPaused()));
        }
        listeners.sort((a, b) -> a.id().compareTo(b.id()));
        Map<String, Long> processed = new LinkedHashMap<>();
        for (String group : lagService.groups()) {
            processed.put(group, processedRepository.countByIdConsumerGroup(group));
        }
        return new PipelineStatus(true,
                outboxRepository.backlog(),
                dlqRepository.countByStatus(DlqMessage.Status.DEAD),
                lagService.lagByGroup(),
                processed,
                listeners);
    }
}
