import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

const ThemeContext = createContext(null);

const STORAGE = { theme: 'sentinel.theme', quality: 'sentinel.gfxQuality' };

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
  const [reducedMotion, setReducedMotion] = useState(prefersReducedMotion);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try { localStorage.setItem(STORAGE.theme, theme); } catch { /* ignore */ }
  }, [theme]);

  useEffect(() => {
    try { localStorage.setItem(STORAGE.quality, quality); } catch { /* ignore */ }
  }, [quality]);

  useEffect(() => {
    let mq;
    try {
      mq = window.matchMedia('(prefers-reduced-motion: reduce)');
      const onChange = () => setReducedMotion(mq.matches);
      mq.addEventListener?.('change', onChange);
      return () => mq.removeEventListener?.('change', onChange);
    } catch {
      return undefined;
    }
  }, []);

  const toggleTheme = useCallback(() => setTheme((t) => (t === 'dark' ? 'light' : 'dark')), []);

  // Effective 3D quality: reduced-motion overrides the stored setting to Off.
  const effectiveQuality = reducedMotion ? 'off' : quality;

  const value = useMemo(
    () => ({ theme, setTheme, toggleTheme, quality, setQuality, reducedMotion, effectiveQuality }),
    [theme, toggleTheme, quality, reducedMotion, effectiveQuality],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme must be used within ThemeProvider');
  }
  return ctx;
}
