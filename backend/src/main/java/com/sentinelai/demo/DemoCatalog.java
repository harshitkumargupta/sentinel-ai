package com.sentinelai.demo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Presentation metadata for the existing simulator scenarios: button label, a one-line description,
 * the rule each one is expected to trigger, and the order of the "full chain". Scenarios the simulator
 * offers but this catalog doesn't know still appear (with their raw id) — the simulator is the source
 * of truth for what can run.
 */
public final class DemoCatalog {

    public record Entry(String label, String description, String expectedRule) {
    }

    /** The baseline scenario used by "Seed sample data" (benign traffic). */
    public static final String BASELINE = "normal";

    /**
     * Scenarios run, in order, by "Run full chain": access attempts → account takeover → abuse.
     * {@code suspicious_login} is excluded: its signal is the 03:00 UTC hour, which re-timing to "now"
     * does not preserve, so it only fires live when the demo happens to run at night (UTC).
     */
    public static final List<String> CHAIN = List.of(
            "credential_stuffing", "brute_force", "impossible_travel",
            "abnormal_access", "api_abuse", "honeytoken");

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        ENTRIES.put("brute_force", new Entry("Brute Force",
                "Burst of failed logins against one account from one IP.", "BRUTE_FORCE"));
        ENTRIES.put("credential_stuffing", new Entry("Credential Stuffing",
                "Failed logins for many different accounts from one IP.", "CREDENTIAL_STUFFING"));
        ENTRIES.put("suspicious_login", new Entry("Suspicious Login",
                "Logins at an odd hour (03:00 UTC). Re-timed to now, it only alerts during 00:00-05:00 UTC.", "SUSPICIOUS_LOGIN"));
        ENTRIES.put("impossible_travel", new Entry("Impossible Travel",
                "Two logins for one user from distant countries, too close in time.", "IMPOSSIBLE_TRAVEL"));
        ENTRIES.put("abnormal_access", new Entry("Abnormal Access",
                "A user reaching resources outside their normal rights.", "ABNORMAL_ACCESS"));
        ENTRIES.put("api_abuse", new Entry("API Abuse",
                "High-frequency API requests from one source.", "HIGH_FREQUENCY_API"));
        ENTRIES.put("honeytoken", new Entry("Honeytoken Access",
                "A planted decoy credential is used.", "HONEYTOKEN"));
        ENTRIES.put("prompt_injection", new Entry("Prompt Injection",
                "Event data carrying a prompt-injection payload aimed at the AI analyst.", null));
        ENTRIES.put(BASELINE, new Entry("Normal Traffic",
                "Benign baseline activity (should raise no incidents).", null));
    }

    private DemoCatalog() {
    }

    public static Entry describe(String scenarioId) {
        return ENTRIES.getOrDefault(scenarioId, new Entry(scenarioId, "Simulator scenario " + scenarioId, null));
    }

    /** Known scenarios first (catalog order), then any extra the simulator offers. */
    public static List<String> ordered(java.util.Collection<String> available) {
        List<String> out = new java.util.ArrayList<>(ENTRIES.keySet().stream().filter(available::contains).toList());
        available.stream().filter(id -> !ENTRIES.containsKey(id)).sorted().forEach(out::add);
        return out;
    }
}
