package com.sentinelai.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Resilience: an unreachable ML service yields empty (never throws) and opens the circuit. */
class HttpMlScoringClientTest {

    @Test
    void unreachableServiceReturnsEmptyAndOpensCircuit() {
        MlProperties props = new MlProperties();
        props.setEnabled(true);
        props.setBaseUrl("http://127.0.0.1:59999"); // nothing listening
        props.setTimeoutMs(100);
        props.setMaxRetries(0);
        props.setCircuitFailureThreshold(2);

        HttpMlScoringClient client = new HttpMlScoringClient(props, new ObjectMapper());

        assertThat(client.score(List.of(0.0), "entity")).isEmpty();
        assertThat(client.score(List.of(0.0), "entity")).isEmpty(); // trips breaker
        // Circuit now open -> still empty, returns immediately.
        assertThat(client.score(List.of(0.0), "entity")).isEmpty();
        assertThat(client.modelInfo()).isEmpty();
    }
}
