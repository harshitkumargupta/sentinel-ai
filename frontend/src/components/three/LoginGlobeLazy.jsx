import { Suspense, lazy } from 'react';
import { useTheme } from '../../theme/ThemeProvider.jsx';
import { hasWebGL } from '../../three/webgl.js';
import ThreeErrorBoundary from '../../three/ThreeErrorBoundary.jsx';

const LoginGlobe = lazy(() => import('../../three/LoginGlobe.jsx'));

const CSS_FALLBACK = (
  <div
    aria-hidden="true"
    style={{
      position: 'absolute',
      inset: 0,
      background: [
        'radial-gradient(55% 55% at 50% 50%, rgba(0,200,232,0.09) 0%, transparent 70%)',
        'radial-gradient(35% 35% at 72% 28%, rgba(255,51,51,0.06) 0%, transparent 60%)',
        'radial-gradient(80% 80% at 50% 65%, rgba(4,12,24,0.95) 0%, #040c18 100%)',
      ].join(','),
    }}
  />
);

/** Lazy-loaded decorative globe for the login page. Falls back to a CSS gradient on no-WebGL / quality=off. */
export default function LoginGlobeLazy() {
  const { effectiveQuality } = useTheme();
  if (effectiveQuality === 'off' || !hasWebGL()) return CSS_FALLBACK;
  return (
    <ThreeErrorBoundary fallback={CSS_FALLBACK}>
      <Suspense fallback={CSS_FALLBACK}>
        <LoginGlobe quality={effectiveQuality} />
      </Suspense>
    </ThreeErrorBoundary>
  );
}
