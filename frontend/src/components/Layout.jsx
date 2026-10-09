import { Suspense } from 'react';
import { motion } from 'framer-motion';
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
          <div key={location.pathname}>{outlet}</div>
        ) : (
          // No AnimatePresence — React StrictMode + FM11 AnimatePresence exit tracking
          // causes orphaned elements that stack off-screen. Enter-only animation (fade-in
          // on key change) is clean and avoids all exit-cleanup race conditions.
          <motion.div
            key={location.pathname}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.18, ease: [0.4, 0, 0.2, 1] }}
          >
            {outlet}
          </motion.div>
        )}
      </main>
    </div>
  );
}
