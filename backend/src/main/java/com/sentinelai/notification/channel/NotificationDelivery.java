package com.sentinelai.notification.channel;

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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** One notification delivery attempt series (status after retries). */
@Entity
@Table(name = "notification_deliveries")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDelivery {

    public enum Status { SENT, MOCKED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(nullable = false, length = 255)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('SENT','MOCKED','FAILED')")
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(nullable = false)
    private boolean test;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
