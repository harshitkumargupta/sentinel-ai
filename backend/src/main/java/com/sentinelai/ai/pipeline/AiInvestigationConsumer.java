package com.sentinelai.ai.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.ai.domain.AnalysisState;
import com.sentinelai.ai.repository.AiAnalysisRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka worker for investigations (when the pipeline is enabled). Reads {@code {analysisId}} from
 * {@code ai.investigations} and runs the pipeline. On failure the analysis is marked FAILED and the
 * offset is still committed, so a poison request never stalls the partition.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class AiInvestigationConsumer {

    private final InvestigationService investigationService;
    private final AiAnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;

    public AiInvestigationConsumer(InvestigationService investigationService,
                                   AiAnalysisRepository analysisRepository, ObjectMapper objectMapper) {
        this.investigationService = investigationService;
        this.analysisRepository = analysisRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(id = "ai-investigation", topics = "${sentinel.kafka.topics.ai-investigations}",
            groupId = "${sentinel.kafka.groups.ai-investigation}")
    public void onInvestigation(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Long analysisId = null;
        try {
            JsonNode node = objectMapper.readTree(record.value());
            analysisId = node.path("analysisId").asLong();
            investigationService.run(analysisId);
        } catch (Exception e) {
            log.error("AI investigation consumer failed for {}", record.value(), e);
            markFailed(analysisId);
        } finally {
            ack.acknowledge();
        }
    }

    @Transactional
    protected void markFailed(Long analysisId) {
        if (analysisId == null) {
            return;
        }
        analysisRepository.findById(analysisId).ifPresent(a -> {
            if (a.getAnalysisState() != AnalysisState.COMPLETE) {
                a.setAnalysisState(AnalysisState.FAILED);
                analysisRepository.save(a);
            }
        });
    }
}
