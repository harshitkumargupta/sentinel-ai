package com.sentinelai.kafka.admin;

import com.sentinelai.kafka.dlq.DlqMessage;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Response DTOs for the Kafka pipeline admin API (never expose entities at the boundary). */
public final class KafkaAdminDtos {

    private KafkaAdminDtos() {
    }

    public record DlqMessageResponse(Long id, String originalTopic, String handler, String messageId,
                                     String kafkaKey, int attempts, String error, String payload,
                                     String status, Instant createdAt, Instant replayedAt) {
        public static DlqMessageResponse from(DlqMessage m) {
            return new DlqMessageResponse(m.getId(), m.getOriginalTopic(), m.getHandler(),
                    m.getMessageId(), m.getKafkaKey(), m.getAttempts(), m.getError(), m.getPayload(),
                    m.getStatus().name(), m.getCreatedAt(), m.getReplayedAt());
        }
    }

    public record ListenerStatus(String id, boolean running, boolean paused) {
    }

    public record PipelineStatus(boolean enabled, long outboxBacklog, long dlqSize,
                                 Map<String, Long> consumerLag, Map<String, Long> processedByGroup,
                                 List<ListenerStatus> listeners) {
    }

    public record ReplayResult(int replayed) {
    }

    public record ChaosBurstResult(int published, String topic) {
    }
}
