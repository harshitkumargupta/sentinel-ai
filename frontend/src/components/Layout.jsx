import { Suspense } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Outlet, useLocation } from 'react-router-dom';
import NavBar from './NavBar.jsx';
import RouteProgress from './RouteProgress.jsx';
import OnboardingTour from './OnboardingTour.jsx';

/**
 * Persistent app shell: the sidebar + top bar stay mounted (so the sliding active pill animates
 * across routes) while only the routed page content fades/slides on navigation. Reduced/Off motion
 * is handled centrally by <MotionConfig>, so this needs no per-page guard.
 */
export default function Layout() {
  const location = useLocation();
  return (
    <div className="app-shell">
      <RouteProgress />
      <OnboardingTour />
      <NavBar />
      <main className="content">
        <AnimatePresence mode="wait">
          <motion.div
            key={location.pathname}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.22, ease: [0.4, 0, 0.2, 1] }}
          >
            <Suspense fallback={<div className="route-fallback" />}>
              <Outlet />
            </Suspense>
          </motion.div>
        </AnimatePresence>
      </main>
    </div>
  );
}
