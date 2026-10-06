import { useEffect, useRef, useState } from 'react';

/** True if the browser can create a WebGL context. Cached after first call. */
let _cached;
export function hasWebGL() {
  if (_cached !== undefined) return _cached;
  try {
    const canvas = document.createElement('canvas');
    _cached = !!(window.WebGLRenderingContext && (canvas.getContext('webgl') || canvas.getContext('experimental-webgl')));
  } catch {
    _cached = false;
  }
  return _cached;
}

/** Cap device pixel ratio by quality to bound GPU work. */
export function pixelRatioCap(quality) {
  const dpr = typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1;
  if (quality === 'low') return Math.min(dpr, 1);
  return Math.min(dpr, 1.75);
}

/**
 * Returns whether a canvas should actively render: false when the tab is hidden or the element is
 * scrolled off-screen, so we never burn GPU/CPU on invisible 3D. Attach the returned ref to a
 * wrapper element.
 */
export function useCanvasActive() {
  const ref = useRef(null);
  const [onScreen, setOnScreen] = useState(true);
  const [visible, setVisible] = useState(!document.hidden);

  useEffect(() => {
    const onVis = () => setVisible(!document.hidden);
    document.addEventListener('visibilitychange', onVis);
    return () => document.removeEventListener('visibilitychange', onVis);
  }, []);

  useEffect(() => {
    const el = ref.current;
    if (!el || typeof IntersectionObserver === 'undefined') return undefined;
    const io = new IntersectionObserver(([e]) => setOnScreen(e.isIntersecting), { threshold: 0.05 });
    io.observe(el);
    return () => io.disconnect();
  }, []);

  return { ref, active: onScreen && visible };
}
