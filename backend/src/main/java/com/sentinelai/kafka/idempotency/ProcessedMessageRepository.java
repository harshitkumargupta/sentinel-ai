package com.sentinelai.kafka.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, ProcessedMessage.Key> {

    long countByIdConsumerGroup(String consumerGroup);
}
