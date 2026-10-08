import { describe, expect, it } from 'vitest';
import { aggregateFlows, arcOpacity, isExpired, pulseSpeed, thicknessForCount } from './flows.js';

describe('aggregateFlows', () => {
  it('merges repeated source countries, summing counts and keeping the top contributor type', () => {
    const out = aggregateFlows([
      { country: 'US', count: 3, topType: 'FAILED_LOGIN' },
      { country: 'us', count: 10, topType: 'BRUTE_FORCE' },
      { country: 'IN', count: 5, topType: 'API_CALL' },
    ]);
    const us = out.find((f) => f.country === 'US');
    expect(us.count).toBe(13);
    expect(us.topType).toBe('BRUTE_FORCE'); // the larger contributor wins
    expect(us.severity).toBe('CRITICAL');
  });

  it('sorts by count descending and caps the result', () => {
    const out = aggregateFlows([
      { country: 'A', count: 1 }, { country: 'B', count: 9 }, { country: 'C', count: 5 },
    ], 2);
    expect(out.map((f) => f.country)).toEqual(['B', 'C']);
  });

  it('ignores rows without a country and tolerates empty input', () => {
    expect(aggregateFlows([{ count: 4 }, { country: '', count: 2 }])).toEqual([]);
    expect(aggregateFlows(null)).toEqual([]);
  });
});

describe('thicknessForCount', () => {
  it('grows with count and stays within bounds', () => {
    expect(thicknessForCount(0)).toBe(1);
    expect(thicknessForCount(1000, { min: 1, max: 6, scale: 50 })).toBeLessThanOrEqual(6);
    expect(thicknessForCount(20)).toBeGreaterThan(thicknessForCount(2));
  });
});

describe('arcOpacity / isExpired (fade logic)', () => {
  it('is fully opaque while fresh and fades linearly to zero', () => {
    expect(arcOpacity(0, 10)).toBe(1);
    expect(arcOpacity(5, 10)).toBeCloseTo(0.5, 3);
    expect(arcOpacity(10, 10)).toBe(0);
    expect(arcOpacity(99, 10)).toBe(0);
  });

  it('disables fading when fadeSeconds <= 0', () => {
    expect(arcOpacity(100, 0)).toBe(1);
  });

  it('expires only once fully faded', () => {
    expect(isExpired(9.9, 10)).toBe(false);
    expect(isExpired(10, 10)).toBe(true);
    expect(isExpired(100, 0)).toBe(false);
  });
});

describe('pulseSpeed', () => {
  it('is bounded between the base and the cap', () => {
    expect(pulseSpeed(0)).toBeCloseTo(0.2, 6);
    expect(pulseSpeed(100000)).toBeCloseTo(0.8, 6);
  });
});
