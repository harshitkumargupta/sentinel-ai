package com.sentinelai.kafka.admin;

import com.sentinelai.audit.service.AuditService;
import com.sentinelai.common.exception.ConflictException;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.kafka.KafkaEventPublisher;
import com.sentinelai.kafka.dlq.DlqMessage;
import com.sentinelai.kafka.dlq.DlqMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Lists and replays dead letters. Replay republishes the original payload to its source topic with
 * the original message id preserved, so it re-enters the pipeline and consumer idempotency still
 * applies. Every replay is audit-logged.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class DlqAdminService {

    private final DlqMessageRepository repository;
    private final KafkaEventPublisher publisher;
    private final AuditService auditService;
    private final Clock clock;

    public DlqAdminService(DlqMessageRepository repository, KafkaEventPublisher publisher,
                           AuditService auditService, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Replay one dead letter by id. */
    @Transactional
    public void replay(Long id, Long orgId, Long actorId, String ip) {
        DlqMessage msg = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dead letter not found: " + id));
        if (msg.getStatus() == DlqMessage.Status.REPLAYED) {
            throw new ConflictException("Dead letter already replayed: " + id);
        }
        republish(msg);
        auditService.record(orgId, actorId, "DLQ_REPLAY", "dlq", id,
                "{\"topic\":\"" + msg.getOriginalTopic() + "\",\"handler\":\"" + msg.getHandler() + "\"}", ip);
    }

    /** Replay every outstanding dead letter. */
    @Transactional
    public int replayAll(Long orgId, Long actorId, String ip) {
        List<DlqMessage> dead = repository.findByStatus(DlqMessage.Status.DEAD);
        for (DlqMessage msg : dead) {
            republish(msg);
        }
        auditService.record(orgId, actorId, "DLQ_REPLAY_ALL", "dlq", null,
                "{\"count\":" + dead.size() + "}", ip);
        return dead.size();
    }

    private void republish(DlqMessage msg) {
        boolean ok = publisher.publish(msg.getOriginalTopic(), msg.getKafkaKey(), msg.getPayload(),
                msg.getMessageId());
        if (!ok) {
            throw new ConflictException("Broker unavailable; retry replay later");
        }
        msg.setStatus(DlqMessage.Status.REPLAYED);
        msg.setReplayedAt(Instant.now(clock));
        log.info("Replayed dead letter {} to {}", msg.getId(), msg.getOriginalTopic());
    }
}
