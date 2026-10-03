package com.sentinelai.detection.engine;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link WindowStore}. Keeps a bounded, time-ordered deque of timestamps per key and
 * prunes entries older than a retention horizon on access, so memory stays bounded.
 */
@Component
public class InMemoryWindowStore implements WindowStore {

    /** Drop observations older than this regardless of query window (safety cap). */
    private static final long RETENTION_SECONDS = 24 * 3600L;

    private final Map<String, Deque<Instant>> store = new ConcurrentHashMap<>();

    @Override
    public synchronized void record(String key, Instant timestamp) {
        Deque<Instant> deque = store.computeIfAbsent(key, k -> new ArrayDeque<>());
        deque.addLast(timestamp);
        prune(deque, timestamp.minusSeconds(RETENTION_SECONDS));
    }

    @Override
    public synchronized long count(String key, Instant from, Instant to) {
        Deque<Instant> deque = store.get(key);
        if (deque == null) {
            return 0;
        }
        long count = 0;
        for (Instant ts : deque) {
            if (!ts.isBefore(from) && !ts.isAfter(to)) {
                count++;
            }
        }
        return count;
    }

    private void prune(Deque<Instant> deque, Instant cutoff) {
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.pollFirst();
        }
    }
}
