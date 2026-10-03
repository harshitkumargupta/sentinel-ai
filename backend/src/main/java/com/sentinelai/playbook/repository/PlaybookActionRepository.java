package com.sentinelai.playbook.repository;

import com.sentinelai.playbook.domain.PlaybookAction;
import com.sentinelai.playbook.domain.PlaybookActionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaybookActionRepository extends JpaRepository<PlaybookAction, Long> {

    List<PlaybookAction> findByIncident_Id(Long incidentId);

    List<PlaybookAction> findByStatus(PlaybookActionStatus status);
}
