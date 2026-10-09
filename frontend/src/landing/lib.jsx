import { useEffect, useRef, useState } from 'react'

export const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v))
export const lerp = (a, b, t) => a + (b - a) * t
export const seg = (p, a, b) => clamp((p - a) / (b - a))
export const ease = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2)
export const easeOut = (t) => 1 - Math.pow(1 - t, 3)

// One shared rAF-throttled scroll loop — all landing sections subscribe here.
// The module-level listener persists for the lifetime of the SPA; individual
// components add/remove their subscriber via the returned cleanup function.
const subs = new Set()
let queued = false
const run = () => { queued = false; subs.forEach((f) => f()) }
if (typeof window !== 'undefined') {
  const q = () => { if (!queued) { queued = true; requestAnimationFrame(run) } }
  window.addEventListener('scroll', q, { passive: true })
  window.addEventListener('resize', q)
}
export function onScroll(f) { subs.add(f); f(); return () => subs.delete(f) }

export function useReducedMotion() {
  const [r, setR] = useState(false)
  useEffect(() => {
    if (typeof window === 'undefined') return
    const m = matchMedia('(prefers-reduced-motion: reduce)')
    setR(m.matches)
    const h = () => setR(m.matches)
    m.addEventListener('change', h)
    return () => m.removeEventListener('change', h)
  }, [])
  return r
}

/** 0 → 1 progress through a tall sticky track. Reduced motion pins to `still`. */
export function useTrack(still = 1) {
  const ref = useRef(null)
  const [p, setP] = useState(0)
  const reduced = useReducedMotion()
  useEffect(() => {
    if (reduced) { setP(still); return }
    return onScroll(() => {
      const el = ref.current
      if (!el) return
      const r = el.getBoundingClientRect()
      if (r.bottom < -200 || r.top > innerHeight + 200) return
      const total = Math.max(1, r.height - innerHeight)
      const v = clamp(-r.top / total)
      setP((o) => (Math.abs(o - v) > 0.0008 ? v : o))
    })
  }, [reduced, still])
  return [ref, p]
}

export function useIsMobile() {
  const [m, setM] = useState(false)
  useEffect(() => {
    if (typeof window === 'undefined') return
    const f = () => setM(innerWidth < 768)
    f(); addEventListener('resize', f)
    return () => removeEventListener('resize', f)
  }, [])
  return m
}

export function rng(seed) {
  return () => { seed = (seed * 16807) % 2147483647; return (seed - 1) / 2147483646 }
}

export function Label({ n, children, className = '' }) {
  return (
    <div className={`font-mono text-[10.5px] tracking-[0.22em] uppercase flex items-center gap-3 ${className}`}>
      <span className="mute">{n}</span>
      <span className="h-px w-8 bg-current opacity-30" />
      <span>{children}</span>
    </div>
  )
}

/** Text that resolves from blur as `t` goes 0 → 1. */
export function Resolve({ t, children, className = '', y = 24 }) {
  const e = easeOut(clamp(t))
  return (
    <div className={className} style={{ opacity: e, filter: `blur(${(1 - e) * 10}px)`, transform: `translate3d(0,${(1 - e) * y}px,0)` }}>
      {children}
    </div>
  )
}
