import { describe, expect, it } from 'vitest';
import { COUNTRY_COORDS, SEV_HEX, coordsFor, latLonToXYZ, severityForType } from './geo.js';

const len = ({ x, y, z }) => Math.sqrt(x * x + y * y + z * z);

describe('latLonToXYZ projection', () => {
  it('puts the north pole at +Y and the south pole at -Y', () => {
    const n = latLonToXYZ(90, 0, 1);
    expect(n.x).toBeCloseTo(0, 6);
    expect(n.y).toBeCloseTo(1, 6);
    expect(n.z).toBeCloseTo(0, 6);
    const s = latLonToXYZ(-90, 0, 1);
    expect(s.y).toBeCloseTo(-1, 6);
  });

  it('places every point on the sphere of the given radius', () => {
    for (const [lat, lon] of [[0, 0], [22, 79], [38, -97], [-10, -55], [51, 0], [61, 100]]) {
      expect(len(latLonToXYZ(lat, lon, 1.4))).toBeCloseTo(1.4, 6);
    }
  });

  it('keeps the Y sign tied to latitude for the verification set (India, US, Brazil, UK, Russia, China)', () => {
    const north = ['IN', 'US', 'GB', 'RU', 'CN'];
    const south = ['BR']; // Brazil centroid is below the equator
    for (const cc of north) {
      const [lat, lon] = COUNTRY_COORDS[cc];
      expect(latLonToXYZ(lat, lon, 1).y).toBeGreaterThan(0);
    }
    for (const cc of south) {
      const [lat, lon] = COUNTRY_COORDS[cc];
      expect(latLonToXYZ(lat, lon, 1).y).toBeLessThan(0);
    }
  });

  it('is rotated by the longitude yaw calibration', () => {
    const base = latLonToXYZ(0, 0, 1, 0);
    const yawed = latLonToXYZ(0, 0, 1, 90);
    expect(yawed.x).not.toBeCloseTo(base.x, 3);
    expect(len(yawed)).toBeCloseTo(1, 6);
  });
});

describe('severity + coords helpers', () => {
  it('maps attack types to severity buckets', () => {
    expect(severityForType('BRUTE_FORCE')).toBe('CRITICAL');
    expect(severityForType('CREDENTIAL_STUFFING')).toBe('CRITICAL');
    expect(severityForType('FAILED_LOGIN')).toBe('HIGH');
    expect(severityForType('API_CALL')).toBe('MEDIUM');
    expect(severityForType('whatever')).toBe('LOW');
  });

  it('uses the required severity colours (LOW green → CRITICAL red)', () => {
    expect(SEV_HEX.LOW).toBe('#3ddc97');
    expect(SEV_HEX.CRITICAL).toBe('#ff5a5f');
  });

  it('resolves known country codes and rejects unknown ones', () => {
    expect(coordsFor('in')).toEqual([22, 79]);
    expect(coordsFor('ZZ')).toBeNull();
    expect(coordsFor(null)).toBeNull();
  });
});
