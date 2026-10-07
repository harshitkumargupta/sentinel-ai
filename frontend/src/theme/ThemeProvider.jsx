import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { MotionConfig } from 'framer-motion';

const ThemeContext = createContext(null);

const STORAGE = { theme: 'sentinel.theme', quality: 'sentinel.gfxQuality', motion: 'sentinel.motion' };

function readStored(key, fallback) {
  try {
    return localStorage.getItem(key) || fallback;
  } catch {
    return fallback;
  }
}

function prefersReducedMotion() {
  try {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  } catch {
    return false;
  }
}

/**
 * App-wide theme + visual preferences. Theme (dark default / light) and the 3D quality setting
 * (High / Low / Off) persist to localStorage. Reduced-motion is honored: when the OS asks for it,
 * 3D is forced Off regardless of the stored quality.
 */
export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(() => readStored(STORAGE.theme, 'dark'));
  const [quality, setQuality] = useState(() => readStored(STORAGE.quality, 'high'));
  const [motion, setMotion] = useState(() => readStored(STORAGE.motion, 'full')); // full | reduced | off
  const [osReducedMotion, setOsReducedMotion] = useState(prefersReducedMotion);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try { localStorage.setItem(STORAGE.theme, theme); } catch { /* ignore */ }
  }, [theme]);

  useEffect(() => {
    try { localStorage.setItem(STORAGE.quality, quality); } catch { /* ignore */ }
  }, [quality]);

  useEffect(() => {
    document.documentElement.dataset.motion = motion;
    try { localStorage.setItem(STORAGE.motion, motion); } catch { /* ignore */ }
  }, [motion]);

  useEffect(() => {
    let mq;
    try {
      mq = window.matchMedia('(prefers-reduced-motion: reduce)');
      const onChange = () => setOsReducedMotion(mq.matches);
      mq.addEventListener?.('change', onChange);
      return () => mq.removeEventListener?.('change', onChange);
    } catch {
      return undefined;
    }
  }, []);

  const toggleTheme = useCallback(() => setTheme((t) => (t === 'dark' ? 'light' : 'dark')), []);

  // Motion applies everywhere: OS preference OR the user's Off/Reduced choice disables movement.
  const motionOff = motion === 'off' || osReducedMotion;
  const reducedMotion = motionOff || motion === 'reduced';
  // Effective 3D quality: reduced/off motion overrides the stored globe quality to Off.
  const effectiveQuality = motionOff ? 'off' : quality;

  const value = useMemo(
    () => ({
      theme, setTheme, toggleTheme, quality, setQuality,
      motion, setMotion, motionOff, reducedMotion, effectiveQuality,
    }),
    [theme, toggleTheme, quality, motion, motionOff, reducedMotion, effectiveQuality],
  );

  return (
    <ThemeContext.Provider value={value}>
      {/* Centrally honour the Motion setting for every framer-motion component. */}
      <MotionConfig reducedMotion={reducedMotion ? 'always' : 'user'}>{children}</MotionConfig>
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme must be used within ThemeProvider');
  }
  return ctx;
}
