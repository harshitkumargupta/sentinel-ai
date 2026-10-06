import { Suspense, lazy } from 'react';
import { useTheme } from '../../theme/ThemeProvider.jsx';
import { hasWebGL } from '../../three/webgl.js';
import ThreeErrorBoundary from '../../three/ThreeErrorBoundary.jsx';

const LoginBackdrop = lazy(() => import('../../three/LoginBackdrop.jsx'));

/** Decorative only: renders the particle scene when 3D is enabled, else a plain CSS gradient. */
export default function LoginBackdropLazy() {
  const { effectiveQuality } = useTheme();
  const gradient = (
    <div aria-hidden="true" style={{ position: 'absolute', inset: 0,
      background: 'radial-gradient(1200px 600px at 70% -10%, color-mix(in srgb, var(--accent) 22%, transparent), transparent 60%)' }} />
  );
  if (effectiveQuality === 'off' || !hasWebGL()) return gradient;
  return (
    <ThreeErrorBoundary fallback={gradient}>
      <Suspense fallback={gradient}>
        <LoginBackdrop quality={effectiveQuality} />
      </Suspense>
    </ThreeErrorBoundary>
  );
}
