package com.sentinelai.asset;

import com.sentinelai.common.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** An inventoried host and/or IP (at least one of the two is set). */
@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    @Column(length = 255)
    private String hostname;

    @Column(length = 45)
    private String ip;

    @Column(length = 150)
    private String owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false,
            columnDefinition = "enum('SERVER','WORKSTATION','LAPTOP','NETWORK','CLOUD','APPLICATION','OTHER')")
    private AssetType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('PRODUCTION','STAGING','DEVELOPMENT','CORPORATE')")
    private AssetEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('LOW','MEDIUM','HIGH','CRITICAL')")
    private AssetCriticality criticality;

    @Column(length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Display name: hostname, else IP. */
    public String label() {
        return hostname != null ? hostname : ip;
    }
}
