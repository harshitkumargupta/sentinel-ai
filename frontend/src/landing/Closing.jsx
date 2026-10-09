import { useTrack, seg, easeOut, Label, Resolve } from './lib'

/* ──────────── 09 ARCHITECTURE ──────────── */
const ARCH = [
  ['Collect', 'Agents & log sources'], ['Ingest', 'Normalize & validate'], ['Kafka / Sync', 'Queue or direct path'],
  ['Detection', 'Rule engine'], ['Correlation', 'Entity + time grouping'], ['Risk / ML / AI', 'Score, signal, investigate'],
  ['Human approval', 'Analyst sign-off'], ['SOAR', 'Controlled response'], ['Audit', 'Hash-chained log'],
]

export function Architecture() {
  const [ref, p] = useTrack(1)
  const t = seg(p, 0.1, 0.9)
  const pos = t * (ARCH.length - 1)
  return (
    <section ref={ref} id="architecture" className="relative h-[260vh]" data-bg="#0A0C0E" data-fg="#F1F0EB" data-ch="06 / ARCHITECTURE">
      <div className="grid-lines pointer-events-none absolute inset-0 opacity-25" />
      <div className="sticky top-0 grid h-screen overflow-hidden px-6 pt-24 pb-8 md:grid-cols-[0.8fr_1.2fr] md:px-12 md:py-0">
        <div className="flex flex-col justify-center md:pr-12">
          <Label n="06">Architecture</Label>
          <h2 className="cond mt-6 text-[clamp(2.6rem,6.5vw,6.8rem)] leading-[0.86] font-[750] tracking-[-0.02em] uppercase">Under the<br />surface.</h2>
          <div className="mt-8 font-mono text-[11px] tracking-[0.14em] uppercase">
            <div className="mute">Tracing</div>
            <div className="mt-1 text-cyan">evt_4a1f · AUTH_FAILURE</div>
            <div className="mute mt-4">Now at</div>
            <div className="semi mt-1 text-[22px] font-[600] tracking-normal">{ARCH[Math.round(pos)][0]}</div>
          </div>
        </div>
        <div className="relative flex flex-col justify-center">
          <div className="relative">
            <div className="absolute top-0 bottom-0 left-[7px] w-px bg-white/10" />
            <div className="absolute top-0 left-[7px] w-px bg-cyan/70 shadow-[0_0_8px_rgba(127,211,230,0.6)]" style={{ height: `${(pos / (ARCH.length - 1)) * 100}%` }} />
            <div className="absolute left-[3px] h-[9px] w-[9px] rounded-full bg-cyan shadow-[0_0_14px_rgba(127,211,230,0.9)]" style={{ top: `calc(${(pos / (ARCH.length - 1)) * 100}% - 4px)` }} />
            {ARCH.map(([n, d], i) => {
              const dist = Math.abs(pos - i)
              const on = Math.max(0, 1 - dist)
              return (
                <div key={n} className="relative flex items-baseline gap-6 py-[clamp(4px,1.1vh,12px)] pl-10" style={{ opacity: i <= pos + 0.5 ? 0.45 + on * 0.55 : 0.18 }}>
                  <span className="font-mono text-[10px] opacity-60">{String(i + 1).padStart(2, '0')}</span>
                  <span className={`semi uppercase transition-[font-size] duration-300 ${on > 0.5 ? 'text-[clamp(1.4rem,2.6vw,2.4rem)] font-[650]' : 'text-[clamp(0.95rem,1.4vw,1.2rem)] font-[500]'} ${i === 6 && on > 0.5 ? 'text-cyan' : ''}`}>{n}</span>
                  <span className="mute hidden font-mono text-[10px] tracking-[0.1em] md:inline">{d}</span>
                </div>
              )
            })}
          </div>
        </div>
      </div>
    </section>
  )
}

/* ──────────── 10 SECURITY ──────────── */
const SEC = [
  ['Authentication', 'Who you are is verified on every request.', ['JWT authentication', 'Rate limiting', 'Input limits']],
  ['Authorization', 'What you can see is bounded by role and tenant.', ['RBAC', 'Tenant isolation']],
  ['Auditability', 'What happened can be proven later.', ['Audit hash chain', 'CSP', 'HSTS']],
  ['AI safety', 'What the model reads cannot command it.', ['Prompt-injection defense', 'Secret scanning']],
]

export function Security() {
  return (
    <section id="security" className="relative" data-bg="#0A0C0E" data-fg="#F1F0EB" data-ch="07 / SECURITY">
      <div className="mx-auto grid max-w-[1600px] gap-10 px-[clamp(1.25rem,5vw,6rem)] py-[18vh] md:grid-cols-[1fr_1.2fr]">
        <div className="flex flex-col md:sticky md:top-[20vh] md:self-start">
          <Label n="07">Security</Label>
          <h2 className="cond mt-6 text-[clamp(2.6rem,7vw,7.2rem)] leading-[0.86] font-[750] tracking-[-0.02em] uppercase">Built for<br />security.</h2>
        </div>
        <div className="flex flex-col justify-center">
          {SEC.map(([h, d, items], i) => (
            <div key={h} className="group border-t border-white/15 py-6 md:py-8">
              <div className="flex items-baseline gap-6">
                <span className="font-mono text-[11px] text-cyan">0{i + 1}</span>
                <span className="cond uppercase transition-all duration-700 text-[clamp(2rem,4vw,3.6rem)] font-[750] group-hover:text-cyan">{h}</span>
              </div>
              <div className="overflow-hidden pl-11">
                <p className="mute mt-2 text-[14px]">{d}</p>
                <div className="mt-3 flex flex-wrap gap-x-6 gap-y-1 font-mono text-[10.5px] tracking-[0.16em] uppercase">
                  {items.map((x) => <span key={x}>— {x}</span>)}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

/* ──────────── 12 FINAL ──────────── */
export function Final() {
  const [ref, p] = useTrack(1)
  return (
    <section ref={ref} className="relative h-[140vh]" data-bg="#000000" data-fg="#F1F0EB" data-ch="08 / ENTER">
      <div className="sticky top-0 flex h-screen flex-col items-center justify-center px-6 text-center">
        <Resolve t={seg(p, 0.05, 0.35)} className="font-mono text-[10px] tracking-[0.3em] uppercase">
          <div>SentinelAI</div><div className="mute mt-1">AI-powered security operations</div>
        </Resolve>
        <Resolve t={seg(p, 0.15, 0.55)} y={40}>
          <h2 className="cond mt-10 text-[clamp(3rem,9vw,9.5rem)] leading-[0.84] font-[750] tracking-[-0.02em] uppercase">Ready to enter<br />the command center?</h2>
        </Resolve>
        <Resolve t={seg(p, 0.35, 0.7)} className="mt-12 flex flex-wrap items-center justify-center gap-8">
          <a href="/login" className="group inline-flex items-center gap-4 bg-[#F1F0EB] px-7 py-4 font-mono text-[11px] tracking-[0.2em] text-black uppercase transition-colors hover:bg-cyan">Enter SentinelAI <span className="transition-transform group-hover:translate-x-1">→</span></a>
          <a href="#architecture" className="font-mono text-[11px] tracking-[0.2em] uppercase opacity-60 hover:opacity-100">Explore architecture</a>
        </Resolve>
        <div className="mute absolute right-6 bottom-6 left-6 flex justify-between font-mono text-[9.5px] tracking-[0.2em] uppercase md:right-12 md:left-12">
          <span>SentinelAI · capstone project</span><span>Self-hosted · open architecture</span>
        </div>
      </div>
    </section>
  )
}
