package com.sentinelai.graph;

import java.time.Instant;
import java.util.List;

public final class GraphDtos {

    private GraphDtos() {
    }

    public record Node(String id, String type, String label) {
    }

    public record Edge(String source, String target, String type, long count, Instant firstSeen) {
    }

    public record KillChainStage(int stage, String tactic, String technique, long count, Instant firstSeen) {
    }

    public record GraphResponse(Long incidentId, List<Node> nodes, List<Edge> edges,
                                List<KillChainStage> killChain, boolean truncated) {
    }
}
