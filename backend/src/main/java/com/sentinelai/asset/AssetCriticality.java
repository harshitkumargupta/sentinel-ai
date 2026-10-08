package com.sentinelai.asset;

/** Business criticality; {@link #level()} (1–4) is the event asset-criticality the risk engine scores. */
public enum AssetCriticality {
    LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4);

    private final int level;

    AssetCriticality(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }
}
