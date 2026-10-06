// NOTE: this module must stay free of `three` imports — it is reached from the main bundle (the 2D
// globe fallback uses COUNTRY_COORDS/severity helpers), so importing three here would pull the whole
// 3D engine into the main chunk. The three.js projection helper lives in AttackGlobe.jsx.

/** Approximate centroid coordinates [lat, lon] for common country codes. */
export const COUNTRY_COORDS = {
  US: [38, -97], CA: [56, -106], BR: [-10, -55], GB: [54, -2], IE: [53, -8], FR: [46, 2],
  DE: [51, 10], NL: [52, 5], ES: [40, -4], IT: [42, 12], SE: [62, 15], PL: [52, 19],
  RU: [61, 100], UA: [49, 32], TR: [39, 35], IN: [22, 79], CN: [35, 104], JP: [36, 138],
  KR: [36, 128], SG: [1.3, 103.8], AU: [-25, 133], ZA: [-30, 25], NG: [9, 8], EG: [26, 30],
  IR: [32, 53], PK: [30, 69], ID: [-2, 118], VN: [16, 106], MX: [23, -102], AR: [-38, -63],
  NO: [62, 10], FI: [64, 26], RO: [46, 25], CZ: [49.8, 15.5], HK: [22.3, 114.2],
};

/** Our protected site (arc destination). */
export const HOME = [37.77, -122.42]; // placeholder HQ

export function coordsFor(country) {
  return COUNTRY_COORDS[String(country || '').toUpperCase()] || null;
}

/** Severity bucket from an event type / count, for arc color. */
export function severityForType(type) {
  const t = String(type || '').toUpperCase();
  if (t.includes('HONEYTOKEN') || t.includes('CREDENTIAL') || t.includes('BRUTE')) return 'CRITICAL';
  if (t.includes('FAILED') || t.includes('SUSPICIOUS') || t.includes('ABNORMAL')) return 'HIGH';
  if (t.includes('API') || t.includes('LOGIN')) return 'MEDIUM';
  return 'LOW';
}

export const SEV_HEX = { LOW: '#38bdf8', MEDIUM: '#f0b429', HIGH: '#ff8c42', CRITICAL: '#ff5a5f' };
