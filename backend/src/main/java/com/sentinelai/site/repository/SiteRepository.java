package com.sentinelai.site.repository;

import com.sentinelai.site.domain.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<Site, Long> {

    List<Site> findByOrg_IdOrderByIdAsc(Long orgId);
}
