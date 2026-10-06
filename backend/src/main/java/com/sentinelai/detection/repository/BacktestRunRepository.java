package com.sentinelai.detection.repository;

import com.sentinelai.detection.domain.BacktestRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BacktestRunRepository extends JpaRepository<BacktestRun, Long> {

    List<BacktestRun> findByRule_Id(Long ruleId);
}
