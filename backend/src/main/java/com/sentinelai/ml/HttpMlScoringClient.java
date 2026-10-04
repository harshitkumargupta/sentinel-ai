package com.sentinelai.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP client for the ML service with a per-call timeout, bounded retries, and a simple circuit
 * breaker. Any failure returns empty (never throws into risk scoring). Active only when
 * {@code sentinel.ml.enabled=true}.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sentinel.ml", name = "enabled", havingValue = "true")
public class HttpMlScoringClient implements MlScoringClient {

    private final MlProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long circuitOpenUntil = 0L;

    public HttpMlScoringClient(MlProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1) // avoid h2c upgrade the ML server rejects
                .connectTimeout(Duration.ofMillis(props.getTimeoutMs())).build();
        log.info("ML scoring enabled -> {}", props.getBaseUrl());
    }

    @Override
    public Optional<MlScore> score(List<Double> features, String kind) {
        if (circuitOpen()) {
            return Optional.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("features", features);
        body.put("kind", kind);
        return post("/score", body).map(this::parseScore);
    }

    @Override
    public Optional<MlModelInfo> modelInfo() {
        if (circuitOpen()) {
            return Optional.empty();
        }
        return get("/model-info").map(this::parseModelInfo);
    }

    private Optional<JsonNode> post(String path, Map<String, Object> body) {
        return send(() -> HttpRequest.newBuilder(URI.create(props.getBaseUrl() + path))
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(body)))
                .build());
    }

    private Optional<JsonNode> get(String path) {
        return send(() -> HttpRequest.newBuilder(URI.create(props.getBaseUrl() + path))
                .timeout(Duration.ofMillis(props.getTimeoutMs())).GET().build());
    }

    private Optional<JsonNode> send(java.util.function.Supplier<HttpRequest> builder) {
        for (int attempt = 0; attempt <= props.getMaxRetries(); attempt++) {
            try {
                HttpResponse<String> resp = http.send(builder.get(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 == 2) {
                    consecutiveFailures.set(0);
                    return Optional.of(objectMapper.readTree(resp.body()));
                }
            } catch (Exception e) {
                log.debug("ML call failed (attempt {}): {}", attempt, e.toString());
            }
        }
        recordFailure();
        return Optional.empty();
    }

    private boolean circuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntil;
    }

    private void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= props.getCircuitFailureThreshold()) {
            circuitOpenUntil = System.currentTimeMillis() + props.getCircuitResetSeconds() * 1000L;
            log.warn("ML circuit breaker OPEN for {}s", props.getCircuitResetSeconds());
        }
    }

    private MlScore parseScore(JsonNode n) {
        List<MlScore.TopFeature> top = new ArrayList<>();
        if (n.has("top_features")) {
            for (JsonNode f : n.get("top_features")) {
                top.add(new MlScore.TopFeature(f.path("name").asText(),
                        f.path("value").asDouble(), f.path("shap").asDouble()));
            }
        }
        return new MlScore((int) Math.round(n.path("score").asDouble()),
                n.path("model_version").asText("unknown"), top);
    }

    private MlModelInfo parseModelInfo(JsonNode n) {
        Map<String, MlModelInfo.FeatureStat> stats = new LinkedHashMap<>();
        JsonNode ts = n.get("train_stats");
        if (ts != null) {
            ts.fields().forEachRemaining(e -> stats.put(e.getKey(),
                    new MlModelInfo.FeatureStat(e.getValue().path("mean").asDouble(),
                            e.getValue().path("std").asDouble(1.0))));
        }
        return new MlModelInfo(n.path("version").asText("unknown"), stats);
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
