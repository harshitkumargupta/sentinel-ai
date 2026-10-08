import { useEffect, useState } from 'react'
import Globe from './Globe'
import { onScroll, clamp, useReducedMotion } from './lib'

const METRICS = [['174', 'Security events'], ['33', 'Alerts'], ['10', 'Incidents'], ['94%', 'Reference-run reduction', true]]

export default function Hero() {
  const reduced = useReducedMotion()
  const [p, setP] = useState(0)
  useEffect(() => (reduced ? undefined : onScroll(() => setP(clamp(scrollY / innerHeight)))), [reduced])

  return (
    <section id="system" className="relative isolate flex min-h-[100svh] flex-col overflow-hidden" data-bg="#050607" data-fg="#F1F0EB" data-ch="00 / SYSTEM">
      {/* globe: right ~58%, softly scales back as the page moves on */}
      <div className="absolute inset-y-0 right-[-18%] left-[-10%] -z-10 md:left-[36%] md:right-[-6%] md:top-[4%]"
        style={{ transform: `translate3d(0,${p * 6}vh,0) scale(${1 - p * 0.06})`, opacity: 1 - p * 0.5 }}>
        <Globe still={reduced} />
      </div>
      {/* atmosphere, vignette, grain */}
      <div className="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(45%_55%_at_70%_55%,rgba(115,215,232,0.05),transparent_70%),radial-gradient(120%_90%_at_50%_40%,transparent_55%,rgba(0,0,0,0.75))]" />
      <div className="pointer-events-none absolute inset-y-0 left-0 -z-10 w-full bg-gradient-to-r from-[#050607] via-[#050607]/60 to-transparent md:w-[62%]" />
      <div className="grain pointer-events-none absolute inset-0 -z-10 opacity-[0.06]" />

      <div className="relative mx-auto flex w-full max-w-[1600px] flex-1 flex-col px-[clamp(1.25rem,5vw,6rem)] pt-[clamp(6rem,13vh,9rem)]"
        style={{ transform: `translate3d(0,${-p * 8}vh,0)` }}>
        <div className="flex items-center gap-4 font-mono text-[10.5px] tracking-[0.24em] uppercase">
          <span className="mute">01</span><span className="h-px w-10 bg-current opacity-30" /><span>AI-powered security operations</span>
        </div>
        <h1 className="cond mt-7 max-w-[11ch] text-[clamp(3rem,min(8vw,12vh),9rem)] leading-[0.86] font-[760] tracking-[-0.015em] uppercase">
          Turn security noise into <span className="text-cyan">decisions.</span>
        </h1>
        <p className="mute mt-[clamp(1.25rem,3.5vh,2rem)] max-w-[440px] text-[clamp(0.95rem,1.1vw,1.06rem)] leading-relaxed">
          SentinelAI transforms raw security events into risk-scored incidents, evidence-backed investigations, and controlled response actions.
        </p>
        <div className="mt-[clamp(1.5rem,4vh,2.25rem)] flex flex-wrap items-center gap-x-10 gap-y-5 pb-[clamp(1.5rem,4vh,2.5rem)]">
          <a href="/login" className="group inline-flex items-center gap-5 bg-[#F1F0EB] px-7 py-[18px] font-mono text-[11px] tracking-[0.22em] text-[#050607] uppercase transition-colors duration-300 hover:bg-cyan">
            Enter command center <span className="transition-transform duration-300 group-hover:translate-x-1">→</span>
          </a>
          <a href="#noise" className="group inline-flex items-center gap-3 font-mono text-[11px] tracking-[0.22em] uppercase opacity-75 transition-opacity hover:opacity-100">
            Explore the system <span className="transition-transform duration-300 group-hover:translate-y-0.5">↓</span>
          </a>
        </div>
      </div>

      {/* reference-run metrics */}
      <div className="relative mx-auto w-full max-w-[1600px] px-[clamp(1.25rem,5vw,6rem)]">
        <div className="grid grid-cols-2 border-t border-white/10 md:grid-cols-4">
          {METRICS.map(([v, l, hi], i) => (
            <div key={l} className={`py-5 pr-4 md:py-[clamp(1rem,3vh,1.75rem)] ${i % 2 ? 'pl-5 md:pl-8' : i ? 'md:pl-8' : ''} ${i ? 'md:border-l' : ''} ${i % 2 ? 'border-l' : ''} border-white/10`}>
              <div className={`semi text-[clamp(1.6rem,2.4vw,2.3rem)] leading-none font-[620] tabular-nums ${hi ? 'text-cyan' : ''}`}>{v}</div>
              <div className="mute mt-2 font-mono text-[9.5px] tracking-[0.2em] uppercase">{l}</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
