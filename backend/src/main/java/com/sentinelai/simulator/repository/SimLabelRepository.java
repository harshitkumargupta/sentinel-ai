package com.sentinelai.simulator.repository;

import com.sentinelai.simulator.domain.SimLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimLabelRepository extends JpaRepository<SimLabel, Long> {

    List<SimLabel> findByRunId(String runId);
}
