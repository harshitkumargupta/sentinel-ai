package com.sentinelai.simulator;

import com.sentinelai.simulator.scenario.BruteForceScenario;
import com.sentinelai.simulator.scenario.CredentialStuffingScenario;
import com.sentinelai.simulator.scenario.NormalTrafficScenario;
import com.sentinelai.simulator.scenario.Scenario;
import com.sentinelai.simulator.scenario.SimContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The same seed must produce identical events (deterministic generation). */
class SimulatorReproducibilityTest {

    @Test
    void sameSeedProducesIdenticalEvents() {
        List<Scenario> scenarios = List.of(
                new NormalTrafficScenario(), new BruteForceScenario(), new CredentialStuffingScenario());

        for (Scenario scenario : scenarios) {
            var a = scenario.generate(new SimContext(12345L, 4));
            var b = scenario.generate(new SimContext(12345L, 4));
            assertThat(a).isEqualTo(b);
            assertThat(a).isNotEmpty();
        }
    }
}
