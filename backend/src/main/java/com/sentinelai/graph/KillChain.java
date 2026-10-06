package com.sentinelai.graph;

import java.util.Map;

/** Maps a MITRE technique id to a kill-chain stage (index + tactic) for ordering the storyline. */
public final class KillChain {

    public record Stage(int index, String tactic) {
    }

    private static final Map<String, Stage> BY_TECHNIQUE = Map.of(
            "T1078", new Stage(1, "Initial Access"),
            "T1078.001", new Stage(2, "Persistence"),
            "T1110", new Stage(3, "Credential Access"),
            "T1110.004", new Stage(3, "Credential Access"),
            "T1548", new Stage(4, "Privilege Escalation"),
            "T1499", new Stage(7, "Impact"));

    private static final Stage UNKNOWN = new Stage(0, "Unknown");

    private KillChain() {
    }

    public static Stage forTechnique(String technique) {
        return technique == null ? UNKNOWN : BY_TECHNIQUE.getOrDefault(technique, UNKNOWN);
    }
}
