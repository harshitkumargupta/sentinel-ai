package com.sentinelai.playbook.repository;

import com.sentinelai.playbook.domain.PlaybookAction;
import com.sentinelai.playbook.domain.PlaybookActionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface PlaybookActionRepository extends JpaRepository<PlaybookAction, Long> {

    List<PlaybookAction> findByIncident_Id(Long incidentId);

    List<PlaybookAction> findByIncident_IdOrderByIdDesc(Long incidentId);

    List<PlaybookAction> findByStatus(PlaybookActionStatus status);

    List<PlaybookAction> findByStatusInAndExpiresAtBefore(
            Collection<PlaybookActionStatus> statuses, Instant cutoff);

    List<PlaybookAction> findByIncident_Org_IdAndApprovedAtNotNull(Long orgId);
}
