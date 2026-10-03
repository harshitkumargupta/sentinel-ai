package com.sentinelai.site.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Grants a user visibility of a site. */
@Entity
@Table(name = "user_site_access")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSiteAccess {

    @EmbeddedId
    private UserSiteAccessId id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserSiteAccess(Long userId, Long siteId) {
        this.id = new UserSiteAccessId(userId, siteId);
    }
}
