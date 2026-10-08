package com.sentinelai.search.saved;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {

    List<SavedSearch> findByOwnerIdOrderByNameAsc(Long ownerId);

    List<SavedSearch> findByOwnerIdAndPinnedTrueOrderByNameAsc(Long ownerId);

    long countByOwnerId(Long ownerId);

    boolean existsByOwnerIdAndName(Long ownerId, String name);
}
