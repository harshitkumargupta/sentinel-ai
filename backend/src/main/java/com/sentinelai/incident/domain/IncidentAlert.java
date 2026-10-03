package com.sentinelai.incident.domain;

import com.sentinelai.alert.domain.Alert;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Links an {@link Alert} to the {@link Incident} it was correlated into. */
@Entity
@Table(name = "incident_alerts")
@Getter
@Setter
@NoArgsConstructor
public class IncidentAlert {

    @EmbeddedId
    private IncidentAlertId id;

    @MapsId("incidentId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @MapsId("alertId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    public IncidentAlert(Incident incident, Alert alert) {
        this.incident = incident;
        this.alert = alert;
        this.id = new IncidentAlertId(incident.getId(), alert.getId());
    }
}
