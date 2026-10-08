package com.sentinelai.simulator;

/**
 * Where a run's generated timestamps land. Scenarios always generate around the fixed
 * {@code SimContext.BASE} so they stay reproducible; the anchor only re-times them at ingest:
 * <ul>
 *   <li>{@code FIXED} — keep the generated timestamps (evaluation / backtests; the default),</li>
 *   <li>{@code NOW} — shift the whole run so its last event is "now" (relative timing preserved,
 *       so detection windows behave exactly as with FIXED; used by the Demo Center),</li>
 *   <li>{@code LAST_24H} — stretch the run across the last 24 hours (baseline seeding only; relative
 *       timing is not preserved).</li>
 * </ul>
 */
public enum TimeAnchor { FIXED, NOW, LAST_24H }
