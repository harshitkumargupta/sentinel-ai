package com.sentinelai.site.repository;

import com.sentinelai.site.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    /** Fetches site + org eagerly so the auth filter can read ids without an open session. */
    @Query("select k from ApiKey k join fetch k.site s join fetch s.org where k.keyHash = :hash")
    Optional<ApiKey> findWithSiteAndOrgByKeyHash(@Param("hash") String hash);

    List<ApiKey> findBySite_Id(Long siteId);
}
