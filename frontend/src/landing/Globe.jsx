import { useEffect, useRef } from 'react'
import { geoOrthographic, geoPath, geoGraticule10, geoContains, geoDistance, geoInterpolate } from 'd3-geo'
import { feature } from 'topojson-client'
import land110 from 'world-atlas/land-110m.json'
import { rng } from './lib'

const NODES = [
  { p: [-74, 40.7], k: 'a', h: 0.9 }, { p: [-122.4, 37.8], k: 'n', h: 0.6 }, { p: [-87.6, 41.9], k: 'a', h: 0.55 },
  { p: [-0.1, 51.5], k: 'n', h: 0.7 }, { p: [8.7, 50.1], k: 'n', h: 0.5 }, { p: [2.35, 48.85], k: 'n', h: 0.35 },
  { p: [37.6, 55.75], k: 'r', h: 0.8 }, { p: [55.3, 25.2], k: 'n', h: 0.4 }, { p: [103.8, 1.35], k: 'n', h: 0.6 },
  { p: [139.7, 35.7], k: 'n', h: 0.55 }, { p: [116.4, 39.9], k: 'r', h: 0.7 }, { p: [77.2, 28.6], k: 'a', h: 0.45 },
  { p: [-46.6, -23.5], k: 'a', h: 0.5 }, { p: [151.2, -33.9], k: 'n', h: 0.4 }, { p: [28, -26.2], k: 'n', h: 0.3 },
  { p: [-99.1, 19.4], k: 'n', h: 0.35 },
]
const ARCS = [[6, 4], [10, 9], [0, 3], [12, 0], [11, 7], [1, 9], [6, 0], [3, 8]]
const COL = { n: '115,215,232', a: '217,138,50', r: '200,77,77' }

export default function Globe({ still = false }) {
  const cv = useRef(null)
  useEffect(() => {
    const c = cv.current
    if (!c) return
    let ctx
    try { ctx = c.getContext('2d') } catch { return }
    if (!ctx) return

    let land, grat
    try {
      land = feature(land110, land110.objects.land)
      grat = geoGraticule10()
    } catch { return }

    // Reduced from 1100 → 600 lights; same visual density at lower CPU cost.
    const r = rng(7)
    const lights = []
    for (let tries = 0; lights.length < 600 && tries < 12000; tries++) {
      const pt = [r() * 360 - 180, Math.asin(r() * 1.7 - 0.85) * 57.3]
      try { if (geoContains(land, pt)) lights.push(pt) } catch { /* ignore */ }
    }
    const proj = geoOrthographic().clipAngle(90).precision(0.8)
    const path = geoPath(proj, ctx)
    let W = 0, H = 0, R = 0, cx = 0, cy = 0, raf = 0, alive = true, inView = false
    const size = () => {
      const dpr = Math.min(devicePixelRatio, 1.5) // cap at 1.5× to reduce fill cost
      W = c.clientWidth; H = c.clientHeight
      c.width = W * dpr; c.height = H * dpr
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      R = Math.min(W * 0.44, H * 0.42)
      cx = W * 0.56; cy = H * 0.5
      proj.scale(R).translate([cx, cy])
    }
    size(); addEventListener('resize', size)
    const start = performance.now()

    const lift = (ll, alt) => {
      const xy = proj(ll); if (!xy) return null
      return [cx + (xy[0] - cx) * (1 + alt), cy + (xy[1] - cy) * (1 + alt)]
    }

    const draw = () => {
      if (!alive || !inView) { raf = 0; return }
      try {
        const t = (performance.now() - start) / 1000
        const rot = [40 - t * (360 / 36), -18, 0]
        proj.rotate(rot)
        const center = [-rot[0], -rot[1]]
        const vis = (ll) => geoDistance(ll, center) < Math.PI / 2 - 0.02
        ctx.clearRect(0, 0, W, H)

        const atm = ctx.createRadialGradient(cx, cy, R * 0.92, cx, cy, R * 1.22)
        atm.addColorStop(0, 'rgba(115,215,232,0.10)'); atm.addColorStop(0.35, 'rgba(115,215,232,0.035)'); atm.addColorStop(1, 'rgba(115,215,232,0)')
        ctx.fillStyle = atm; ctx.fillRect(0, 0, W, H)

        const body = ctx.createRadialGradient(cx - R * 0.45, cy - R * 0.5, R * 0.1, cx, cy, R)
        body.addColorStop(0, '#12171A'); body.addColorStop(0.6, '#0A0D10'); body.addColorStop(1, '#050607')
        ctx.beginPath(); path({ type: 'Sphere' }); ctx.fillStyle = body; ctx.fill()

        ctx.beginPath(); path(grat); ctx.strokeStyle = 'rgba(241,240,235,0.035)'; ctx.lineWidth = 0.6; ctx.stroke()
        ctx.beginPath(); path(land); ctx.fillStyle = 'rgba(160,175,185,0.035)'; ctx.fill()
        ctx.strokeStyle = 'rgba(241,240,235,0.11)'; ctx.lineWidth = 0.5; ctx.stroke()

        // Fast hemisphere pre-check: sin/cos cheaper than full geoDistance for the bulk test
        const [cLon, cLat] = center
        const cLatR = cLat * Math.PI / 180, cLonR = cLon * Math.PI / 180
        const scLat = Math.sin(cLatR), ccLat = Math.cos(cLatR)
        for (let i = 0; i < lights.length; i++) {
          const [lon, lat] = lights[i]
          const latR = lat * Math.PI / 180
          const d = Math.acos(Math.min(1, scLat * Math.sin(latR) + ccLat * Math.cos(latR) * Math.cos((lon - cLon) * Math.PI / 180)))
          if (d > 1.5) continue
          const xy = proj(lights[i]); if (!xy) continue
          const a = Math.cos(d) * 0.8
          ctx.fillStyle = i % 9 === 0 ? `rgba(217,160,90,${a})` : `rgba(225,228,226,${a * 0.6})`
          ctx.fillRect(xy[0], xy[1], 1.1, 1.1)
        }

        const shade = ctx.createLinearGradient(cx - R, cy - R, cx + R, cy + R)
        shade.addColorStop(0.45, 'rgba(5,6,7,0)'); shade.addColorStop(1, 'rgba(5,6,7,0.75)')
        ctx.beginPath(); path({ type: 'Sphere' }); ctx.fillStyle = shade; ctx.fill()

        const rim = ctx.createRadialGradient(cx, cy, R * 0.9, cx, cy, R)
        rim.addColorStop(0, 'rgba(115,215,232,0)'); rim.addColorStop(1, 'rgba(180,232,240,0.28)')
        ctx.beginPath(); path({ type: 'Sphere' }); ctx.fillStyle = rim; ctx.fill()

        ARCS.forEach(([a, b], i) => {
          const A = NODES[a].p, B = NODES[b].p
          const ip = geoInterpolate(A, B)
          const span = geoDistance(A, B)
          ctx.beginPath(); let pen = false
          for (let s = 0; s <= 32; s++) { // reduced from 40 → 32 segments
            const ll = ip(s / 32)
            const xy = vis(ll) ? lift(ll, Math.sin((s / 32) * Math.PI) * span * 0.06) : null
            if (!xy) { pen = false; continue }
            pen ? ctx.lineTo(xy[0], xy[1]) : ctx.moveTo(xy[0], xy[1]); pen = true
          }
          const threat = NODES[a].k !== 'n'
          ctx.strokeStyle = threat ? 'rgba(217,138,50,0.32)' : 'rgba(115,215,232,0.28)'; ctx.lineWidth = 0.8; ctx.stroke()
          const ph = ((t / 6 + i * 0.37) % 1.6)
          if (ph <= 1) {
            const ll = ip(ph)
            const xy = vis(ll) && lift(ll, Math.sin(ph * Math.PI) * span * 0.06)
            if (xy) { ctx.fillStyle = threat ? 'rgba(240,170,90,0.95)' : 'rgba(170,235,245,0.95)'; ctx.fillRect(xy[0] - 1.2, xy[1] - 1.2, 2.4, 2.4) }
          }
        })

        NODES.forEach((n, i) => {
          if (!vis(n.p)) return
          const base = proj(n.p)
          const pulse = 0.85 + Math.sin(t * 0.9 + i * 1.7) * 0.15
          const top = lift(n.p, n.h * 0.24 * pulse)
          const g = ctx.createLinearGradient(base[0], base[1], top[0], top[1])
          g.addColorStop(0, `rgba(${COL[n.k]},0.9)`); g.addColorStop(1, `rgba(${COL[n.k]},0)`)
          ctx.strokeStyle = g; ctx.lineWidth = n.k === 'n' ? 1 : 1.6
          ctx.beginPath(); ctx.moveTo(base[0], base[1]); ctx.lineTo(top[0], top[1]); ctx.stroke()
          ctx.fillStyle = `rgba(${COL[n.k]},1)`; ctx.fillRect(base[0] - 1.5, base[1] - 1.5, 3, 3)
          if (n.k !== 'n') {
            const ring = (t * 0.35 + i * 0.2) % 1
            ctx.strokeStyle = `rgba(${COL[n.k]},${(1 - ring) * 0.5})`; ctx.lineWidth = 0.8
            ctx.beginPath(); ctx.ellipse(base[0], base[1], 3 + ring * 14, (3 + ring * 14) * 0.55, 0, 0, Math.PI * 2); ctx.stroke()
          }
        })
      } catch { /* canvas errors are non-fatal */ }
      if (!still && alive && inView) raf = requestAnimationFrame(draw)
      else raf = 0
    }

    // Pause the loop when the canvas leaves the viewport — largest single perf win.
    const io = new IntersectionObserver(([e]) => {
      inView = e.isIntersecting
      if (inView && !still && !raf && alive) { raf = requestAnimationFrame(draw) }
      else if (!inView && raf) { cancelAnimationFrame(raf); raf = 0 }
    }, { rootMargin: '100px' })
    io.observe(c)

    // Also pause when the tab is hidden.
    const onVis = () => {
      if (document.hidden) { cancelAnimationFrame(raf); raf = 0 }
      else if (inView && !still && alive && !raf) { raf = requestAnimationFrame(draw) }
    }
    document.addEventListener('visibilitychange', onVis)

    // The IntersectionObserver fires asynchronously; do an immediate check
    // so the globe starts rendering on mount without waiting for the first IO callback.
    if (c.getBoundingClientRect().top < innerHeight + 100) {
      inView = true
      if (!still) raf = requestAnimationFrame(draw)
      else draw()
    }

    return () => {
      alive = false
      cancelAnimationFrame(raf)
      removeEventListener('resize', size)
      io.disconnect()
      document.removeEventListener('visibilitychange', onVis)
    }
  }, [still])
  return <canvas ref={cv} className="absolute inset-0 h-full w-full" aria-hidden />
}
