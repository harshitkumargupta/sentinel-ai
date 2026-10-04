package com.sentinelai.kafka.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

/**
 * Consumer-side idempotency marker. A row's presence means {@code (consumerGroup, messageId)} has
 * already been processed, so a duplicate delivery becomes a no-op. The insert happens in the same
 * transaction as the side effect, so either both commit or neither does.
 */
@Entity
@Table(name = "processed_messages")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedMessage {

    @EmbeddedId
    private Key id;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedMessage(String consumerGroup, String messageId) {
        this.id = new Key(consumerGroup, messageId);
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "consumer_group", nullable = false, length = 100)
        private String consumerGroup;
        @Column(name = "message_id", nullable = false, length = 100)
        private String messageId;
    }
}
