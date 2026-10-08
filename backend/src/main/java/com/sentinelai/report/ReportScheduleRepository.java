package com.sentinelai.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, Long> {

    List<ReportSchedule> findByOrgIdOrderByIdAsc(Long orgId);

    List<ReportSchedule> findByEnabledTrueAndNextRunAtLessThanEqual(Instant now);
}
