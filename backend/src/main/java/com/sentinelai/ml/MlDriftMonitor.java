package com.sentinelai.ml;

import com.sentinelai.common.domain.Severity;
import com.sentinelai.event.domain.EventType;
import com.sentinelai.event.domain.SecurityEvent;
import com.sentinelai.event.repository.SecurityEventRepository;
import com.sentinelai.event.service.SecurityEventRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled drift check: compares live feature means to the model's training stats and raises a
 * self-monitored security event when any feature drifts beyond the configured sigma threshold.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MlDriftMonitor {

    private static final Long DEFAULT_ORG = 1L;

    private final MlProperties properties;
    private final MlScoringClient client;
    private final SecurityEventRepository eventRepository;
    private final SecurityEventRecorder eventRecorder;

    @Scheduled(cron = "${sentinel.ml.drift-cron:0 */15 * * * *}")
    public void check() {
        if (!properties.isEnabled()) {
            return;
        }
        MlModelInfo info = client.modelInfo().orElse(null);
        if (info == null) {
            return;
        }
        List<SecurityEvent> sample = eventRepository
                .findAll(PageRequest.of(0, properties.getDriftSampleSize(), Sort.by(Sort.Direction.DESC, "id")))
                .getContent();
        if (sample.isEmpty()) {
            return;
        }

        // Live means for the cheaply per-event-derivable features.
        double failed = sample.stream().filter(e -> e.getEventType() == EventType.FAILED_LOGIN).count()
                / (double) sample.size();
        double honey = sample.stream().filter(SecurityEvent::isHoneytoken).count() / (double) sample.size();
        double asset = sample.stream().map(e -> e.getAssetCriticality() == null ? 0 : (int) e.getAssetCriticality())
                .mapToInt(Integer::intValue).average().orElse(0) / 4.0;

        check("failed_login_ratio", failed, info);
        check("honeytoken_flag", honey, info);
        check("asset_criticality", asset, info);
    }

    private void check(String feature, double liveMean, MlModelInfo info) {
        MlModelInfo.FeatureStat stat = info.trainStats().get(feature);
        if (stat == null) {
            return;
        }
        double sigma = Math.abs(liveMean - stat.mean()) / Math.max(stat.std(), 1e-6);
        if (sigma > properties.getDriftSigmaThreshold()) {
            log.warn("ML feature drift on {}: live={} train_mean={} ({}σ)", feature, liveMean, stat.mean(), sigma);
            eventRecorder.record(DEFAULT_ORG, EventType.OTHER, Severity.MEDIUM, "ml-drift", "self",
                    "ml/drift",
                    "{\"feature\":\"" + feature + "\",\"liveMean\":" + liveMean
                            + ",\"trainMean\":" + stat.mean() + ",\"sigma\":" + sigma + "}");
        }
    }
}
