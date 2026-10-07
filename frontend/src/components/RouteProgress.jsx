import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useLocation } from 'react-router-dom';

/** Slim gradient progress bar that sweeps across the top on each route change / chunk load. */
export default function RouteProgress() {
  const location = useLocation();
  const [active, setActive] = useState(false);
  useEffect(() => {
    setActive(true);
    const t = setTimeout(() => setActive(false), 450);
    return () => clearTimeout(t);
  }, [location.pathname]);
  return (
    <AnimatePresence>
      {active && (
        <motion.div
          className="route-progress" aria-hidden="true"
          initial={{ scaleX: 0, opacity: 1 }}
          animate={{ scaleX: 1 }}
          exit={{ opacity: 0 }}
          transition={{ scaleX: { duration: 0.45, ease: [0.4, 0, 0.2, 1] }, opacity: { duration: 0.2 } }}
        />
      )}
    </AnimatePresence>
  );
}
