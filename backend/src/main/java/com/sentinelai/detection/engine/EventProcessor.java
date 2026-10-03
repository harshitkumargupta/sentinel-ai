package com.sentinelai.detection.engine;

import com.sentinelai.event.domain.SecurityEvent;

/**
 * Processes a single event through the detection rules. Synchronous for now; this seam lets a
 * Kafka-backed asynchronous processor replace it later without changing ingestion or the rules.
 */
public interface EventProcessor {

    void process(SecurityEvent event);
}
