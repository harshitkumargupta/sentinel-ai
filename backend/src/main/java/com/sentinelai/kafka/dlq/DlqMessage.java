package com.sentinelai.kafka.dlq;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A dead letter persisted from {@code events.dlq}: the message that exhausted its retries, together
 * with the error, attempt count and original headers, so it can be inspected and replayed by an
 * admin without touching the broker.
 */
@Entity
@Table(name = "dlq_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DlqMessage {

    public enum Status { DEAD, REPLAYED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_topic", nullable = false, length = 100)
    private String originalTopic;

    @Column(length = 50)
    private String handler;

    @Column(name = "message_id", length = 100)
    private String messageId;

    @Column(name = "kafka_key", length = 255)
    private String kafkaKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String payload;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 1000)
    private String error;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String headers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('DEAD','REPLAYED')")
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "replayed_at")
    private Instant replayedAt;
}
