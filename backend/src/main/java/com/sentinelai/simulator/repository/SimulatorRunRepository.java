package com.sentinelai.simulator.repository;

import com.sentinelai.simulator.domain.SimulatorRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimulatorRunRepository extends JpaRepository<SimulatorRun, Long> {

    List<SimulatorRun> findTop50ByOrderByIdDesc();
}
