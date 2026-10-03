package com.sentinelai.site.repository;

import com.sentinelai.site.domain.UserSiteAccess;
import com.sentinelai.site.domain.UserSiteAccessId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserSiteAccessRepository extends JpaRepository<UserSiteAccess, UserSiteAccessId> {

    List<UserSiteAccess> findById_UserId(Long userId);

    boolean existsById_UserIdAndId_SiteId(Long userId, Long siteId);
}
