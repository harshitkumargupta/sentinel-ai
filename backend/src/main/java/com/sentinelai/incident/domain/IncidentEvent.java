package com.sentinelai.incident.domain;

import com.sentinelai.event.domain.SecurityEvent;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Join entity linking an {@link Incident} to a {@link SecurityEvent} (many-to-many with an
 * extra {@code added_at} attribute). Modeled explicitly rather than with {@code @ManyToMany}
 * so the link carries its own data and both sides can stay LAZY without a bidirectional loop.
 */
@Entity
@Table(name = "incident_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IncidentEvent {

    @EmbeddedId
    private IncidentEventId id;

    @MapsId("incidentId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @MapsId("eventId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private SecurityEvent event;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    public IncidentEvent(Incident incident, SecurityEvent event) {
        this.incident = incident;
        this.event = event;
        this.id = new IncidentEventId(incident.getId(), event.getId());
    }
}
