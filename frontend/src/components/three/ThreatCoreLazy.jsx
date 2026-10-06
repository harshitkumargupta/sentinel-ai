import { Suspense, lazy } from 'react';
import { useTheme } from '../../theme/ThemeProvider.jsx';
import { hasWebGL, useCanvasActive } from '../../three/webgl.js';
import ThreeErrorBoundary from '../../three/ThreeErrorBoundary.jsx';

// Lazy so three.js/@react-three never land in the main chunk.
const ThreatCore = lazy(() => import('../../three/ThreatCore.jsx'));

const LEVEL_COLOR = { LOW: 'var(--sev-low)', MEDIUM: 'var(--sev-medium)', HIGH: 'var(--sev-high)', CRITICAL: 'var(--sev-critical)' };

/** Static CSS orb — the fallback when 3D is off/unavailable, and the data-equivalent indicator. */
function StaticOrb({ size, level }) {
  return (
    <span role="img" aria-label={`Threat level ${level}`} title={`Threat level ${level}`}
      style={{ display: 'inline-block', width: size, height: size, borderRadius: '50%',
        background: `radial-gradient(circle at 35% 35%, ${LEVEL_COLOR[level] || LEVEL_COLOR.LOW}, transparent 70%)`,
        border: `1px solid ${LEVEL_COLOR[level] || LEVEL_COLOR.LOW}` }} />
  );
}

/**
 * Header risk orb. Renders the 3D core only when quality != off, WebGL is available, and the element
 * is on-screen/visible; otherwise (and on any 3D error) a static CSS orb with the same meaning.
 */
export default function ThreatCoreLazy({ size = 28, level = 'LOW' }) {
  const { effectiveQuality } = useTheme();
  const { ref, active } = useCanvasActive();
  const enabled = effectiveQuality !== 'off' && hasWebGL();
  const fallback = <StaticOrb size={size} level={level} />;

  if (!enabled) return <span ref={ref}>{fallback}</span>;
  return (
    <span ref={ref} style={{ width: size, height: size, display: 'inline-block' }}>
      <ThreeErrorBoundary fallback={fallback}>
        <Suspense fallback={fallback}>
          <ThreatCore size={size} level={level} active={active} quality={effectiveQuality} />
        </Suspense>
      </ThreeErrorBoundary>
    </span>
  );
}
