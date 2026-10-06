package com.sentinelai.kafka.dlq;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DlqMessageRepository extends JpaRepository<DlqMessage, Long> {

    Page<DlqMessage> findByStatusOrderByIdDesc(DlqMessage.Status status, Pageable pageable);

    List<DlqMessage> findByStatus(DlqMessage.Status status);

    long countByStatus(DlqMessage.Status status);
}
