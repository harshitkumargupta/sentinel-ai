package com.sentinelai.site.repository;

import com.sentinelai.site.domain.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<Site, Long> {

    List<Site> findByOrg_IdOrderByIdAsc(Long orgId);

    java.util.Optional<Site> findFirstByOrg_IdAndName(Long orgId, String name);

    /** Atomic counter bump for records a source sent that could not be parsed or accepted. */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update Site s set s.parseErrorCount = s.parseErrorCount + :n where s.id = :id")
    int addParseErrors(@org.springframework.data.repository.query.Param("id") Long id,
                       @org.springframework.data.repository.query.Param("n") long n);
}
