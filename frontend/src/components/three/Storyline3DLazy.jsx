import { Suspense, lazy } from 'react';
import { useTheme } from '../../theme/ThemeProvider.jsx';
import { hasWebGL, useCanvasActive } from '../../three/webgl.js';
import ThreeErrorBoundary from '../../three/ThreeErrorBoundary.jsx';
import StorylineGraph from '../StorylineGraph.jsx';

const Storyline3D = lazy(() => import('../../three/Storyline3D.jsx'));

/**
 * 3D storyline wrapper. Renders the 3D scene only when 3D is enabled and WebGL is available;
 * otherwise (and on any error / reduced motion) falls back to the 2D SVG storyline — same data.
 */
export default function Storyline3DLazy({ graph, height = 420 }) {
  const { effectiveQuality } = useTheme();
  const { ref, active } = useCanvasActive();
  const enabled = effectiveQuality !== 'off' && hasWebGL();
  const fallback = <StorylineGraph graph={graph} />;

  if (!enabled) return fallback;
  return (
    <div ref={ref} style={{ height }}>
      <ThreeErrorBoundary fallback={fallback}>
        <Suspense fallback={<div className="ui-skel" style={{ width: '100%', height: '100%' }} />}>
          <Storyline3D graph={graph} active={active} quality={effectiveQuality} />
        </Suspense>
      </ThreeErrorBoundary>
    </div>
  );
}
