import { Suspense, lazy, useEffect, useMemo, useState } from 'react';
import { useTheme } from '../../theme/ThemeProvider.jsx';
import { hasWebGL, useCanvasActive } from '../../three/webgl.js';
import ThreeErrorBoundary from '../../three/ThreeErrorBoundary.jsx';
import { SEV_HEX } from '../../three/geo.js';
import { GLOBE_CONFIG } from '../../three/globeConfig.js';
import { aggregateFlows } from '../../three/flows.js';

const AttackGlobe = lazy(() => import('../../three/AttackGlobe.jsx'));
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

/** Ranked table of attack origins — the accessible text alternative and the non-WebGL fallback. */
export function GlobeTable({ flows, onSelect }) {
  const rows = useMemo(() => aggregateFlows(flows, 12), [flows]);
  if (!rows.length) return <div className="ui-state">No geo-located attack origins yet.</div>;
  return (
    <table className="ui-table" aria-label="Attack origins">
      <thead><tr><th>Origin</th><th>Attacks</th><th>Top type</th><th>Severity</th></tr></thead>
      <tbody>
        {rows.map((f) => (
          <tr key={f.country} onClick={() => onSelect?.(f.country)}
            style={onSelect ? { cursor: 'pointer' } : undefined}>
            <td>{f.country}</td>
            <td>{f.count}</td>
            <td>{f.topType || '—'}</td>
            <td style={{ color: SEV_HEX[f.severity] }}>{f.severity}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** Side panel: top attacking countries, the severity legend, and the live/paused toggle. */
function GlobePanel({ flows, paused, onToggle, onSelect }) {
  const top = useMemo(() => aggregateFlows(flows, GLOBE_CONFIG.topCountries), [flows]);
  return (
    <aside className="globe-panel" aria-label="Attack origins summary">
      <div className="globe-panel__head">
        <span>Top origins</span>
        <button className="ui-btn ui-btn--sm" onClick={onToggle} aria-pressed={paused}
          title={paused ? 'Resume live animation' : 'Pause animation'}>
          {paused ? '⏸ Paused' : '● Live'}
        </button>
      </div>
      {top.length === 0 ? (
        <div className="ui-state">No origins yet.</div>
      ) : (
        <ol className="globe-panel__list">
          {top.map((f) => (
            <li key={f.country}>
              <button type="button" className="globe-panel__row" onClick={() => onSelect?.(f.country)}>
                <span className="globe-panel__dot" style={{ background: SEV_HEX[f.severity] }} />
                <span className="globe-panel__cc">{f.country}</span>
                <span className="globe-panel__count">{f.count}</span>
              </button>
            </li>
          ))}
        </ol>
      )}
      <div className="globe-legend" aria-label="Severity legend">
        {SEVERITIES.map((s) => (
          <span key={s} className="globe-legend__item">
            <span className="globe-panel__dot" style={{ background: SEV_HEX[s] }} />{s}
          </span>
        ))}
      </div>
      <p className="globe-panel__dest">→ {GLOBE_CONFIG.site.name}</p>
    </aside>
  );
}

/** Static view (Off / no-WebGL / reduced-motion): a still of the model plus the flows table. */
function StaticView({ flows, onSelect }) {
  const [imgOk, setImgOk] = useState(true);
  return (
    <div className="globe-static">
      {imgOk && (
        <img src={GLOBE_CONFIG.staticImage} alt="Earth with recent attack origins" loading="lazy"
          className="globe-static__img" onError={() => setImgOk(false)} />
      )}
      <GlobeTable flows={flows} onSelect={onSelect} />
    </div>
  );
}

/**
 * AttackGlobe with graceful degradation. Renders the 3D earth only when quality != Off, WebGL is
 * available and the element is on-screen; otherwise (Off, no WebGL, reduced motion, or any 3D/model
 * load error) it shows the static image + flows table — never a procedural globe. The side panel
 * (top origins, legend, live/paused) is always present as the text alternative.
 */
export default function AttackGlobeLazy({ flows, height = 360, onSelectCountry }) {
  const { effectiveQuality } = useTheme();
  const { ref, active } = useCanvasActive();
  const [paused, setPaused] = useState(false);
  // Data-first: show the table immediately, upgrade to 3D when the browser is idle so the three.js
  // chunk never blocks first paint.
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const ric = window.requestIdleCallback || ((cb) => setTimeout(cb, 400));
    const cancel = window.cancelIdleCallback || clearTimeout;
    const id = ric(() => setReady(true), { timeout: 1500 });
    return () => cancel(id);
  }, []);

  const enabled = ready && effectiveQuality !== 'off' && hasWebGL();
  // If the model fails to load, fall back to the static table with an error state (no procedural globe).
  const errorFallback = (
    <div className="globe-static">
      <div className="ui-state ui-state--error">Could not load the 3D earth model — showing the flows table.</div>
      <GlobeTable flows={flows} onSelect={onSelectCountry} />
    </div>
  );

  return (
    <div className="globe-layout" style={{ minHeight: height }}>
      <div ref={ref} className="globe-stage" style={{ height }}>
        {enabled ? (
          <ThreeErrorBoundary fallback={errorFallback}>
            <Suspense fallback={<div className="ui-skel" style={{ width: '100%', height: '100%' }} />}>
              <AttackGlobe flows={flows} active={active && !paused} quality={effectiveQuality}
                onSelect={onSelectCountry} />
            </Suspense>
          </ThreeErrorBoundary>
        ) : (
          <StaticView flows={flows} onSelect={onSelectCountry} />
        )}
      </div>
      <GlobePanel flows={flows} paused={paused} onToggle={() => setPaused((p) => !p)}
        onSelect={onSelectCountry} />
    </div>
  );
}
