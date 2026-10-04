package com.sentinelai.kafka.admin;

import com.sentinelai.kafka.dlq.DlqMessage;
import com.sentinelai.kafka.dlq.DlqMessageRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Registers pipeline gauges with Micrometer (exposed via Actuator for the later Prometheus step):
 * consumer lag per group and the dead-letter backlog. Publish/consume/retry/DLQ counters and
 * processing timers are recorded inline at their call sites; the outbox-backlog gauge lives on the
 * relay.
 */
@Component
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class PipelineMetrics {

    private final MeterRegistry meters;
    private final KafkaLagService lagService;
    private final DlqMessageRepository dlqRepository;

    public PipelineMetrics(MeterRegistry meters, KafkaLagService lagService,
                           DlqMessageRepository dlqRepository) {
        this.meters = meters;
        this.lagService = lagService;
        this.dlqRepository = dlqRepository;
    }

    @PostConstruct
    void register() {
        for (String group : lagService.groups()) {
            Gauge.builder("sentinel.kafka.consumer.lag", lagService, s -> s.lagFor(group))
                    .tag("group", group)
                    .description("Committed-to-latest offset lag for a consumer group")
                    .register(meters);
        }
        Gauge.builder("sentinel.kafka.dlq.size", dlqRepository,
                        r -> r.countByStatus(DlqMessage.Status.DEAD))
                .description("Dead letters awaiting replay")
                .register(meters);
    }
}
