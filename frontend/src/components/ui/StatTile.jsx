import { useEffect, useRef, useState } from 'react';
import { useTheme } from '../../theme/ThemeProvider.jsx';

/**
 * Stat tile with an animated number counter. Honors reduced motion (snaps to the final value).
 * `delta` (optional) shows a small up/down change indicator; `invertDelta` flips the good/bad color
 * (e.g. a rising FP rate is bad = up/red, a falling MTTR is good = down/green).
 */
export default function StatTile({ label, value, suffix = '', delta, invertDelta = false, format }) {
  const { reducedMotion } = useTheme();
  const numeric = typeof value === 'number' ? value : null;
  const [display, setDisplay] = useState(numeric ?? 0);
  const prev = useRef(numeric ?? 0);

  useEffect(() => {
    if (numeric === null) return undefined;
    if (reducedMotion) { setDisplay(numeric); prev.current = numeric; return undefined; }
    const from = prev.current;
    const to = numeric;
    const start = performance.now();
    const dur = 600;
    let raf;
    const tick = (t) => {
      const p = Math.min(1, (t - start) / dur);
      const eased = 1 - Math.pow(1 - p, 3);
      setDisplay(from + (to - from) * eased);
      if (p < 1) raf = requestAnimationFrame(tick);
      else prev.current = to;
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [numeric, reducedMotion]);

  const shown = numeric === null
    ? value
    : (format ? format(display) : Math.round(display).toLocaleString());

  return (
    <div className="ui-stat">
      <span className="ui-stat__label">{label}</span>
      <span className="ui-stat__value">{shown}{suffix}</span>
      {delta != null && delta !== 0 && (
        <span className={`ui-stat__delta ui-stat__delta--${(delta > 0) === !invertDelta ? 'up' : 'down'}`}>
          {delta > 0 ? '▲' : '▼'} {Math.abs(delta)}{suffix}
        </span>
      )}
    </div>
  );
}
