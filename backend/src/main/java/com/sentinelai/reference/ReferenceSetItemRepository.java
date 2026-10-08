package com.sentinelai.reference;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReferenceSetItemRepository extends JpaRepository<ReferenceSetItem, Long> {

    Page<ReferenceSetItem> findBySet_IdOrderByIdDesc(Long setId, Pageable pageable);

    long countBySet_Id(Long setId);

    boolean existsBySet_IdAndValue(Long setId, String value);

    /** CIDR entries of a set (values containing '/'), for IP range matching. */
    @Query("select i.value from ReferenceSetItem i where i.set.id = :setId and i.value like '%/%'")
    List<String> cidrValues(@Param("setId") Long setId);
}
