package com.sentinelai.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary key for {@link IncidentEvent} (incident_id + event_id).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class IncidentEventId implements Serializable {

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "event_id")
    private Long eventId;
}
