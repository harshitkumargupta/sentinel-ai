package com.sentinelai.detection.tuning;

import com.sentinelai.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically recomputes tuning suggestions and logs the noisiest rule per org, so operators are
 * nudged toward high-FP rules even without opening the dashboard. Suggestions are never auto-applied.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TuningRecomputeJob {

    private final TuningService tuningService;
    private final OrganizationRepository organizationRepository;

    @Scheduled(cron = "${sentinel.tuning.cron:0 0 * * * *}")
    public void recompute() {
        organizationRepository.findAll().forEach(org -> {
            var tunings = tuningService.forOrg(org.getId());
            tunings.stream().filter(t -> t.enoughSamples() && t.suggestion() != null).findFirst()
                    .ifPresent(t -> log.info("Tuning suggestion for org {} rule '{}': {}",
                            org.getId(), t.ruleName(), t.suggestion().summary()));
        });
    }
}
