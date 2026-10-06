package com.sentinelai.playbook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically expires stale PROPOSED/APPROVED actions so nothing lingers approved indefinitely. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaybookExpiryJob {

    private final PlaybookService playbookService;

    @Scheduled(fixedDelayString = "${sentinel.playbook.expiry-check-ms:60000}")
    public void expire() {
        int expired = playbookService.expireStale();
        if (expired > 0) {
            log.info("Expired {} stale playbook action(s)", expired);
        }
    }
}
