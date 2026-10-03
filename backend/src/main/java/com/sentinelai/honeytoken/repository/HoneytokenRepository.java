package com.sentinelai.honeytoken.repository;

import com.sentinelai.honeytoken.domain.Honeytoken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoneytokenRepository extends JpaRepository<Honeytoken, Long> {

    List<Honeytoken> findByOrg_Id(Long orgId);

    Optional<Honeytoken> findByValueHash(String valueHash);
}
