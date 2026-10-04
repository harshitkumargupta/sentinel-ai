package com.sentinelai.kafka.consumer;

import com.sentinelai.kafka.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a consumer's side effect exactly once per {@code (group, messageId)}. The seen-check, the
 * work, and the processed-marker all commit in one transaction: a duplicate delivery is a no-op,
 * and if the work throws, the transaction (including the marker) rolls back so a retry can redo it
 * cleanly.
 */
@Service
@RequiredArgsConstructor
public class IdempotentExecutor {

    private final IdempotencyService idempotency;

    /**
     * @return true if the work ran, false if this message was already processed by this group.
     */
    @Transactional
    public boolean execute(String group, String messageId, ThrowingRunnable work) throws Exception {
        if (idempotency.alreadyProcessed(group, messageId)) {
            return false;
        }
        work.run();
        idempotency.markProcessed(group, messageId);
        return true;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
