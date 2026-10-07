// NOTE: this module must stay free of `three` imports — it is reached from the main chunk (the 2D
// globe fallback and the side panel use these helpers), so importing three here would pull the whole
// 3D engine into the main bundle. The pure lat/lon → XYZ projection lives here so it can be unit
// tested without a WebGL/three runtime; AttackGlobe.jsx wraps the result in a THREE.Vector3.

const DEG = Math.PI / 180;

/** Approximate centroid coordinates [lat, lon] for common country codes. */
export const COUNTRY_COORDS = {
  US: [38, -97], CA: [56, -106], BR: [-10, -55], GB: [54, -2], IE: [53, -8], FR: [46, 2],
  DE: [51, 10], NL: [52, 5], ES: [40, -4], IT: [42, 12], SE: [62, 15], PL: [52, 19],
  RU: [61, 100], UA: [49, 32], TR: [39, 35], IN: [22, 79], CN: [35, 104], JP: [36, 138],
  KR: [36, 128], SG: [1.3, 103.8], AU: [-25, 133], ZA: [-30, 25], NG: [9, 8], EG: [26, 30],
  IR: [32, 53], PK: [30, 69], ID: [-2, 118], VN: [16, 106], MX: [23, -102], AR: [-38, -63],
  NO: [62, 10], FI: [64, 26], RO: [46, 25], CZ: [49.8, 15.5], HK: [22.3, 114.2],
};

export function coordsFor(country) {
  return COUNTRY_COORDS[String(country || '').toUpperCase()] || null;
}

/**
 * Project geographic [lat, lon] onto a sphere of the given radius, returning a plain {x, y, z}.
 * Convention (verified against the earth model's orientation): north pole at +Y, lat/lon in degrees.
 * `yawDeg` rotates around the polar axis to calibrate the overlay to the model's texture seam.
 */
export function latLonToXYZ(lat, lon, radius = 1, yawDeg = 0) {
  const phi = (90 - lat) * DEG;
  const theta = (lon + 180 + yawDeg) * DEG;
  return {
    x: -radius * Math.sin(phi) * Math.cos(theta),
    y: radius * Math.cos(phi),
    z: radius * Math.sin(phi) * Math.sin(theta),
  };
}

/** Severity bucket from an event type / count, for arc color. */
export function severityForType(type) {
  const t = String(type || '').toUpperCase();
  if (t.includes('HONEYTOKEN') || t.includes('CREDENTIAL') || t.includes('BRUTE')) return 'CRITICAL';
  if (t.includes('FAILED') || t.includes('SUSPICIOUS') || t.includes('ABNORMAL')) return 'HIGH';
  if (t.includes('API') || t.includes('LOGIN')) return 'MEDIUM';
  return 'LOW';
}

export const SEV_HEX = { LOW: '#3ddc97', MEDIUM: '#f0b429', HIGH: '#ff8c42', CRITICAL: '#ff5a5f' };
