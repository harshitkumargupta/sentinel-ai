// Central configuration for the attack globe. No magic numbers live in the components — every
// tunable is here, and each is overridable at build time via a Vite env var (VITE_GLOBE_*), so the
// protected-site location, arc caps, fade window and tessellation can change per environment
// without touching code. Kept free of `three` so it is safe to import from the main chunk.

function num(value, fallback) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

const env = (typeof import.meta !== 'undefined' && import.meta.env) || {};

export const GLOBE_CONFIG = {
  // Protected site = the destination all attack arcs point to. Defaults to India (New Delhi).
  site: {
    name: env.VITE_SITE_NAME || 'Protected site — India',
    lat: num(env.VITE_SITE_LAT, 28.61),
    lon: num(env.VITE_SITE_LON, 77.21),
  },
  // Cap on concurrent arcs so a flood of origins never overwhelms the GPU; extra origins still
  // appear in the side panel / table.
  maxArcs: num(env.VITE_GLOBE_MAX_ARCS, 40),
  // A line with no fresh traffic fades out over this many seconds, then is dropped.
  fadeSeconds: num(env.VITE_GLOBE_FADE_SECONDS, 12),
  // How many origins the side panel lists.
  topCountries: num(env.VITE_GLOBE_TOP_COUNTRIES, 8),
  // Arc tessellation and pulse speed per quality tier (Low spends far fewer segments).
  arcSegments: { high: num(env.VITE_GLOBE_ARC_SEGMENTS_HIGH, 48), low: num(env.VITE_GLOBE_ARC_SEGMENTS_LOW, 18) },
  // Longitude calibration (degrees) aligning the lat/lon overlay to the model's cubic-UV texture.
  // Verified against India, US, Brazil, UK, Russia and China (see docs/screenshots).
  yawDeg: num(env.VITE_GLOBE_YAW_DEG, -80),
  // Static image shown in Off / no-WebGL / reduced-motion mode.
  staticImage: env.VITE_GLOBE_STATIC_IMAGE || '/models/earth-static.png',
};
