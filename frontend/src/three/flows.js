// Pure, three-free helpers for turning raw geo-flow rows into the data the globe and side panel
// render: de-duplication/aggregation of repeated source→site pairs, line thickness from event
// count, and the time-based fade of a line once its origin stops producing traffic. Kept pure so it
// is deterministic and unit-testable without a WebGL runtime.

import { severityForType } from './geo.js';

/**
 * Collapse repeated source countries into one entry per country: counts are summed and the most
 * common event type (the top contributor's) is kept. Result is sorted by count desc and capped.
 */
export function aggregateFlows(flows, cap = 40) {
  const byCountry = new Map();
  for (const f of flows || []) {
    const country = String(f?.country || '').toUpperCase();
    if (!country) continue;
    const count = Number(f.count) || 0;
    const prev = byCountry.get(country);
    if (!prev) {
      byCountry.set(country, { country, count, topType: f.topType || null, _topCount: count });
    } else {
      prev.count += count;
      if (count > prev._topCount) {
        prev.topType = f.topType || prev.topType;
        prev._topCount = count;
      }
    }
  }
  return [...byCountry.values()]
    .map(({ _topCount, ...rest }) => ({ ...rest, severity: severityForType(rest.topType) }))
    .sort((a, b) => b.count - a.count || a.country.localeCompare(b.country))
    .slice(0, Math.max(0, cap));
}

/** Line thickness from event count: log-scaled between min and max so one huge origin can't dwarf the rest. */
export function thicknessForCount(count, { min = 1, max = 6, scale = 50 } = {}) {
  const c = Math.max(0, Number(count) || 0);
  const t = Math.log2(1 + c) / Math.log2(1 + scale);
  const w = min + (max - min) * Math.min(1, t);
  return Math.round(w * 100) / 100;
}

/**
 * Opacity of a line given how long (seconds) since its origin last produced traffic. Fresh lines
 * are fully opaque; once idle they fade linearly to 0 over `fadeSeconds`, after which the caller
 * drops them. `fadeSeconds <= 0` disables fading (always fully visible).
 */
export function arcOpacity(ageSeconds, fadeSeconds) {
  if (!(fadeSeconds > 0)) return 1;
  if (ageSeconds <= 0) return 1;
  if (ageSeconds >= fadeSeconds) return 0;
  return Math.round((1 - ageSeconds / fadeSeconds) * 1000) / 1000;
}

/** True once a line has fully faded and should be removed. */
export function isExpired(ageSeconds, fadeSeconds) {
  return fadeSeconds > 0 && ageSeconds >= fadeSeconds;
}

/** Pulse travel speed (fraction of the arc per second): busier origins pulse faster, bounded. */
export function pulseSpeed(count) {
  return 0.2 + Math.min(0.6, (Number(count) || 0) / 50);
}
