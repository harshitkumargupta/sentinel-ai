package com.sentinelai.baseline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary key for {@link EntityBaseline} (entity_key + metric).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EntityBaselineId implements Serializable {

    @Column(name = "entity_key", length = 255)
    private String entityKey;

    @Column(length = 100)
    private String metric;
}
