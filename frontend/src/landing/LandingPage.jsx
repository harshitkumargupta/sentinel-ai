import { useEffect, useRef, useState } from 'react'
import Hero from './Hero'
import { Noise, Pipeline } from './Story'
import { Investigation, Storyline, Product } from './Story2'
import { Architecture, Security, Final } from './Closing'
import { onScroll, clamp } from './lib'
import LandingErrorBoundary from './LandingErrorBoundary'
import './landing.css'

const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16))
const mix = (a, b, t) => {
  const A = hex(a), B = hex(b)
  return A.map((v, i) => Math.round(v + (B[i] - v) * t))
}

/**
 * Interpolates --bg / --fg across section boundaries on the LANDING CONTAINER
 * element (not document.documentElement) to prevent variable bleed into the
 * SentinelAI SOC application on navigation.
 */
function useBackgroundController(containerRef) {
  const [state, setState] = useState({ ch: '00 / NOISE', scrolled: false, progress: 0, light: false })
  useEffect(() => {
    const el = containerRef.current
    if (!el) return

    // Set initial values immediately so the page doesn't flash
    el.style.setProperty('--bg', '#050607')
    el.style.setProperty('--fg', '#F1F0EB')
    el.style.setProperty('--mute', 'rgba(241, 240, 235, 0.5)')
    el.style.setProperty('--line', 'rgba(241, 240, 235, 0.12)')

    const cleanup = onScroll(() => {
      const container = containerRef.current
      if (!container) return
      const secs = Array.from(document.querySelectorAll('[data-bg]'))
      if (secs.length === 0) return

      const mid = innerHeight * 0.5
      const vh = innerHeight
      let i = secs.findIndex((s) => s.getBoundingClientRect().bottom > mid)
      if (i < 0) i = secs.length - 1
      const cur = secs[i], next = secs[i + 1]
      let t = 0
      if (next) t = clamp((mid - (next.getBoundingClientRect().top - vh * 0.55)) / (vh * 0.55))

      try {
        const bg = next ? mix(cur.dataset.bg, next.dataset.bg, t) : hex(cur.dataset.bg)
        const fg = next ? mix(cur.dataset.fg, next.dataset.fg, t) : hex(cur.dataset.fg)
        const lum = (bg[0] * 0.3 + bg[1] * 0.59 + bg[2] * 0.11) / 255

        container.style.setProperty('--bg', `rgb(${bg})`)
        container.style.setProperty('--fg', `rgb(${fg})`)
        container.style.setProperty('--mute', `rgba(${fg},0.55)`)
        container.style.setProperty('--line', `rgba(${fg},0.13)`)

        const max = document.documentElement.scrollHeight - vh
        setState((o) => {
          const n = {
            ch: (t > 0.5 && next ? next : cur).dataset.ch || '',
            scrolled: scrollY > 40,
            progress: max > 0 ? scrollY / max : 0,
            light: lum > 0.5,
          }
          return o.ch === n.ch && o.scrolled === n.scrolled && o.light === n.light && Math.abs(o.progress - n.progress) < 0.002 ? o : n
        })
      } catch { /* non-fatal */ }
    })

    return cleanup
  }, [containerRef])

  return state
}

function Nav({ ch, scrolled, progress }) {
  return (
    <header className={`fixed inset-x-0 top-0 z-50 transition-[background,backdrop-filter,border-color] duration-500 ${scrolled ? 'bl border-b bg-[color-mix(in_srgb,var(--bg)_72%,transparent)] backdrop-blur-md' : 'border-b border-transparent'}`}>
      <div className="fg mx-auto flex h-16 max-w-[1600px] items-center gap-8 px-[clamp(1.25rem,5vw,6rem)]">
        <a href="/" className="cond text-[17px] font-[800] tracking-[0.08em]">SENTINEL<span className="text-cyan">AI</span></a>
        <span className="relative mute hidden items-center gap-3 border-l bl pl-6 font-mono text-[9.5px] tracking-[0.22em] whitespace-nowrap uppercase xl:flex">
          <span className={`transition-opacity duration-500 ${scrolled ? 'opacity-0' : ''}`}>AI-powered security operations</span>
          <span className={`absolute transition-opacity duration-500 ${scrolled ? '' : 'opacity-0'}`}>{ch}</span>
        </span>
        <nav className="ml-auto hidden items-center gap-8 font-mono text-[10.5px] tracking-[0.2em] uppercase lg:flex">
          {[['System', '#system'], ['Capabilities', '#capabilities'], ['Architecture', '#architecture'], ['Security', '#security']].map(([l, h]) => (
            <a key={l} href={h} className="opacity-60 transition-opacity hover:opacity-100">{l}</a>
          ))}
        </nav>
        <span className="mute hidden items-center gap-2 font-mono text-[9.5px] tracking-[0.18em] uppercase 2xl:flex">
          <span className="blink h-1.5 w-1.5 rounded-full bg-[#4FBF8A]" />Operational
        </span>
        <a href="/login" className="ml-auto border border-current/40 px-4 py-2 font-mono text-[10.5px] tracking-[0.2em] whitespace-nowrap uppercase transition-colors hover:border-current lg:ml-0">
          <span className="hidden sm:inline">Enter command center </span><span className="sm:hidden">Enter </span>→
        </a>
      </div>
      {/* scroll progress bar */}
      <div className="absolute bottom-[-1px] left-0 h-px bg-cyan" style={{ width: `${progress * 100}%` }} />
    </header>
  )
}

export default function LandingPage() {
  const containerRef = useRef(null)
  const s = useBackgroundController(containerRef)

  // Ensure scroll starts at top when landing mounts (user may have scrolled while in SOC)
  useEffect(() => {
    window.scrollTo(0, 0)
    // Restore body overflow in case SOC app had it hidden
    document.body.style.overflow = ''

    return () => {
      // Reset body scroll position on unmount so the SOC app starts at top
      window.scrollTo(0, 0)
    }
  }, [])

  return (
    <LandingErrorBoundary>
      <div
        ref={containerRef}
        className="landing-page fg overflow-x-clip"
        style={{ background: 'var(--bg)', color: 'var(--fg)', minHeight: '100svh' }}
      >
        <Nav {...s} />
        <main>
          <Hero />
          <Noise />
          <Pipeline />
          <Investigation />
          <Storyline />
          <Product />
          <Architecture />
          <Security />
          <Final />
        </main>
      </div>
    </LandingErrorBoundary>
  )
}
