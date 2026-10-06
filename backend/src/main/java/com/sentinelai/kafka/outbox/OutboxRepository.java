package com.sentinelai.kafka.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findByStatusOrderByIdAsc(OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);

    @Query("select count(o) from OutboxMessage o where o.status = com.sentinelai.kafka.outbox.OutboxStatus.PENDING")
    long backlog();

    @Query("select o from OutboxMessage o where o.aggregateType = :type and o.aggregateId = :id")
    List<OutboxMessage> findByAggregate(@Param("type") String type, @Param("id") Long id);
}
