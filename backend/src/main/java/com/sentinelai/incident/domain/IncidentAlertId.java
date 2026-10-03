package com.sentinelai.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Composite key for {@link IncidentAlert} (incident_id + alert_id). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class IncidentAlertId implements Serializable {

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "alert_id")
    private Long alertId;
}
