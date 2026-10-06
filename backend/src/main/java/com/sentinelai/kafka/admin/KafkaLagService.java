package com.sentinelai.kafka.admin;

import com.sentinelai.kafka.KafkaProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reports per-group consumer lag (committed vs. latest offset) via the Kafka admin client. Results
 * are cached briefly so repeated metric scrapes and status calls don't hammer the broker.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "sentinel.kafka", name = "enabled", havingValue = "true")
public class KafkaLagService {

    private static final long CACHE_MS = 5_000;

    private final AdminClient admin;
    private final List<String> groups;

    private volatile Map<String, Long> cached = Map.of();
    private volatile long cachedAt = 0;

    public KafkaLagService(KafkaProperties props) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());
        cfg.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        cfg.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5_000);
        this.admin = AdminClient.create(cfg);
        KafkaProperties.Groups g = props.getGroups();
        this.groups = List.of(g.getRawIngest(), g.getDetection(), g.getAnalytics(),
                g.getCorrelation(), g.getNotification(), g.getRetry(), g.getDlq());
    }

    public List<String> groups() {
        return groups;
    }

    /** Lag per group (cached). A group with no committed offsets yet reports 0. */
    public Map<String, Long> lagByGroup() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_MS) {
            return cached;
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (String group : groups) {
            result.put(group, lagFor(group));
        }
        cached = result;
        cachedAt = now;
        return result;
    }

    public long lagFor(String group) {
        // Served from cache when fresh to keep gauge scrapes cheap.
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_MS && cached.containsKey(group)) {
            return cached.get(group);
        }
        try {
            ListConsumerGroupOffsetsResult offsets = admin.listConsumerGroupOffsets(group);
            Map<TopicPartition, OffsetAndMetadata> committed =
                    offsets.partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
            if (committed == null || committed.isEmpty()) {
                return 0;
            }
            Map<TopicPartition, OffsetSpec> latestSpec = new HashMap<>();
            committed.keySet().forEach(tp -> latestSpec.put(tp, OffsetSpec.latest()));
            var ends = admin.listOffsets(latestSpec).all().get(5, TimeUnit.SECONDS);

            long lag = 0;
            for (var e : committed.entrySet()) {
                long end = ends.containsKey(e.getKey()) ? ends.get(e.getKey()).offset() : 0;
                long pos = e.getValue() == null ? 0 : e.getValue().offset();
                lag += Math.max(0, end - pos);
            }
            return lag;
        } catch (Exception ex) {
            log.debug("Lag lookup failed for group {}: {}", group, ex.toString());
            return 0;
        }
    }

    @PreDestroy
    void close() {
        admin.close();
    }
}
