import { Suspense } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Outlet, useLocation } from 'react-router-dom';
import NavBar from './NavBar.jsx';
import RouteProgress from './RouteProgress.jsx';
import OnboardingTour from './OnboardingTour.jsx';
import { useTheme } from '../theme/ThemeProvider.jsx';

export default function Layout() {
  const location = useLocation();
  const { motionOff } = useTheme();

  const outlet = (
    <Suspense fallback={<div className="route-fallback" />}>
      <Outlet />
    </Suspense>
  );

  return (
    <div className="app-shell">
      <RouteProgress />
      <OnboardingTour />
      <NavBar />
      <main className="content">
        {motionOff ? (
          // When motion is off, bypass Framer Motion entirely.
          // AnimatePresence(mode="wait") + MotionConfig(reducedMotion="always") interact
          // in Framer Motion 11 in a way that leaves entering pages stuck at opacity:0.
          <div key={location.pathname}>{outlet}</div>
        ) : (
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.22, ease: [0.4, 0, 0.2, 1] }}
            >
              {outlet}
            </motion.div>
          </AnimatePresence>
        )}
      </main>
    </div>
  );
}
