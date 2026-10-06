package com.sentinelai.ai.pipeline;

import com.sentinelai.kafka.EventPublisher;
import com.sentinelai.kafka.KafkaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Runs an investigation asynchronously: via a Kafka topic when the pipeline is enabled and healthy
 * (so it scales and survives restarts), otherwise on a local thread pool. Either way the work lands
 * in {@link InvestigationService#run(Long)}.
 */
@Slf4j
@Component
public class InvestigationDispatcher {

    private final KafkaProperties kafkaProps;
    private final ObjectProvider<EventPublisher> eventPublisher;
    private final Executor aiExecutor;
    private final InvestigationService investigationService;

    public InvestigationDispatcher(KafkaProperties kafkaProps,
                                   ObjectProvider<EventPublisher> eventPublisher,
                                   @Qualifier("aiExecutor") Executor aiExecutor,
                                   InvestigationService investigationService) {
        this.kafkaProps = kafkaProps;
        this.eventPublisher = eventPublisher;
        this.aiExecutor = aiExecutor;
        this.investigationService = investigationService;
    }

    public void dispatch(Long analysisId) {
        EventPublisher publisher = eventPublisher.getIfAvailable();
        if (kafkaProps.isEnabled() && publisher != null && publisher.isHealthy()) {
            publisher.publish(kafkaProps.getTopics().getAiInvestigations(), "ai-" + analysisId,
                    Map.of("analysisId", analysisId), "ai-" + analysisId);
            log.debug("Dispatched investigation {} via Kafka", analysisId);
        } else {
            aiExecutor.execute(() -> {
                try {
                    investigationService.run(analysisId);
                } catch (Exception e) {
                    log.error("Investigation {} failed", analysisId, e);
                }
            });
            log.debug("Dispatched investigation {} via thread pool", analysisId);
        }
    }
}
