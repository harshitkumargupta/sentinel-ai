package com.sentinelai.baseline;

import com.sentinelai.baseline.domain.EntityBaseline;
import com.sentinelai.baseline.domain.EntityBaselineId;
import com.sentinelai.baseline.repository.EntityBaselineRepository;
import com.sentinelai.redis.RedisGateway;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Welford baselines with in-memory working state (authoritative, deterministic), a best-effort Redis
 * hot mirror, and periodic persistence to {@code entity_baselines}. State is reloaded from the DB on
 * startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehavioralBaselineServiceImpl implements BehavioralBaselineService {

    private final EntityBaselineRepository repository;
    private final RedisGateway redis;
    private final BaselineProperties props;

    private final Map<String, WelfordState> states = new ConcurrentHashMap<>();

    @PostConstruct
    void load() {
        for (EntityBaseline b : repository.findAll()) {
            long count = b.getSampleCount() == null ? 0 : b.getSampleCount();
            double mean = b.getMeanValue() == null ? 0 : b.getMeanValue();
            double std = b.getStdDev() == null ? 0 : b.getStdDev();
            double m2 = count > 1 ? std * std * (count - 1) : 0;
            states.put(key(b.getId().getEntityKey(), b.getId().getMetric()),
                    new WelfordState(count, mean, m2));
        }
        log.info("Loaded {} behavioral baselines", states.size());
    }

    @Override
    public void observe(String entityKey, String metric, double value) {
        WelfordState state = states.computeIfAbsent(key(entityKey, metric), k -> new WelfordState());
        state.observe(value);
        String rk = "bl:" + key(entityKey, metric);
        redis.call(t -> {
            t.opsForHash().putAll(rk, Map.of("count", String.valueOf(state.count()),
                    "mean", String.valueOf(state.mean()), "m2", String.valueOf(state.m2())));
            return true;
        }, "baseline.mirror");
    }

    @Override
    public Optional<Double> zScore(String entityKey, String metric, double value) {
        WelfordState state = states.get(key(entityKey, metric));
        if (state == null || state.count() < props.getMinSamples()) {
            return Optional.empty(); // cold start -> skip, don't flag
        }
        return Optional.of(state.zScore(value));
    }

    @Override
    public Optional<Stat> stat(String entityKey, String metric) {
        WelfordState s = states.get(key(entityKey, metric));
        return s == null ? Optional.empty() : Optional.of(new Stat(s.count(), s.mean(), s.std()));
    }

    @Scheduled(cron = "${sentinel.baseline.flush-cron:0 */5 * * * *}")
    @Transactional
    public void flush() {
        states.forEach((key, state) -> {
            int sep = key.lastIndexOf('|');
            String entityKey = key.substring(0, sep);
            String metric = key.substring(sep + 1);
            repository.save(EntityBaseline.builder()
                    .id(new EntityBaselineId(entityKey, metric))
                    .meanValue(state.mean()).stdDev(state.std()).sampleCount(state.count())
                    .build());
        });
    }

    private String key(String entityKey, String metric) {
        return entityKey + "|" + metric;
    }
}
