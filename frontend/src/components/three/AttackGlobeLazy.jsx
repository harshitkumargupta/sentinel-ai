import { Suspense, lazy, useEffect, useState } from 'react';
import { useTheme } from '../../theme/ThemeProvider.jsx';
import { hasWebGL, useCanvasActive } from '../../three/webgl.js';
import ThreeErrorBoundary from '../../three/ThreeErrorBoundary.jsx';
import { SEV_HEX, severityForType } from '../../three/geo.js';

const AttackGlobe = lazy(() => import('../../three/AttackGlobe.jsx'));

/** 2D fallback / accessible equivalent: a ranked table of attack origins. */
export function GlobeTable({ flows }) {
  if (!flows?.length) return <div className="ui-state">No geo-located attack origins yet.</div>;
  return (
    <table className="ui-table" aria-label="Attack origins">
      <thead><tr><th>Origin</th><th>Events</th><th>Top type</th><th>Severity</th></tr></thead>
      <tbody>
        {flows.slice(0, 12).map((f) => (
          <tr key={f.country}>
            <td>{f.country}</td>
            <td>{f.count}</td>
            <td>{f.topType}</td>
            <td style={{ color: SEV_HEX[severityForType(f.topType)] }}>{severityForType(f.topType)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/**
 * AttackGlobe with graceful degradation: renders the 3D globe only when quality != off and WebGL is
 * available and the element is on-screen; otherwise (and on any 3D error, or reduced motion) it
 * shows the 2D origins table — which is also the screen-reader/text alternative for the same data.
 */
export default function AttackGlobeLazy({ flows, height = 360 }) {
  const { effectiveQuality } = useTheme();
  const { ref, active } = useCanvasActive();
  // Data-first: show the 2D table immediately and upgrade to the 3D globe when the browser is idle,
  // so the ~200 KB three.js chunk never blocks first paint / interactivity.
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const ric = window.requestIdleCallback || ((cb) => setTimeout(cb, 400));
    const cancel = window.cancelIdleCallback || clearTimeout;
    const id = ric(() => setReady(true), { timeout: 1500 });
    return () => cancel(id);
  }, []);

  const enabled = ready && effectiveQuality !== 'off' && hasWebGL();
  const fallback = <GlobeTable flows={flows} />;

  return (
    <div ref={ref} style={{ height, position: 'relative' }}>
      {enabled ? (
        <ThreeErrorBoundary fallback={fallback}>
          <Suspense fallback={<div className="ui-skel" style={{ width: '100%', height: '100%' }} />}>
            <AttackGlobe flows={flows} active={active} quality={effectiveQuality} />
          </Suspense>
        </ThreeErrorBoundary>
      ) : fallback}
    </div>
  );
}
