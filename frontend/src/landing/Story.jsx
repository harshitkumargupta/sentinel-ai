import { useMemo } from 'react'
import { useTrack, seg, ease, easeOut, lerp, rng, Label, Resolve, clamp, useIsMobile } from './lib'

/* ──────────── 01 NOISE — 174 events → 33 alerts → 10 incidents ──────────── */
function useReduction() {
  return useMemo(() => {
    const r = rng(17)
    const inc = Array.from({ length: 10 }, (_, i) => {
      const a = (i / 10) * Math.PI * 2 + 0.3
      return { x: 300 + Math.cos(a) * (120 + r() * 60), y: 300 + Math.sin(a) * (120 + r() * 60) }
    })
    const alerts = Array.from({ length: 33 }, (_, i) => {
      const k = i % 10
      return { k, x: 40 + r() * 520, y: 40 + r() * 520 }
    })
    const events = Array.from({ length: 174 }, (_, i) => {
      const a = i % 33
      return { a, x: 10 + r() * 580, y: 10 + r() * 580, j: r() }
    })
    return { inc, alerts, events }
  }, [])
}

export function Noise() {
  const [ref, p] = useTrack(1)
  const { inc, alerts, events } = useReduction()
  const gather = ease(seg(p, 0.12, 0.42))
  const alertOn = seg(p, 0.36, 0.46)
  const merge = ease(seg(p, 0.52, 0.78))
  const incOn = seg(p, 0.72, 0.84)
  const count = p < 0.42 ? Math.round(lerp(174, 33, ease(seg(p, 0.3, 0.42)))) : Math.round(lerp(33, 10, ease(seg(p, 0.66, 0.8))))
  const unit = p < 0.42 ? 'Security events' : p < 0.8 ? 'Alerts' : 'Incidents'
  const swap = seg(p, 0.44, 0.52)

  const aPos = alerts.map((a) => ({ x: lerp(a.x, inc[a.k].x, merge), y: lerp(a.y, inc[a.k].y, merge) }))

  return (
    <section ref={ref} id="noise" className="relative h-[320vh]" data-bg="#121518" data-fg="#F1F0EB" data-ch="01 / SECURITY NOISE">
      <div className="sticky top-0 grid h-screen grid-rows-[auto_1fr] overflow-hidden px-6 pt-24 md:grid-cols-[1.15fr_1fr] md:grid-rows-1 md:px-12 md:pt-0">
        <div className="relative flex flex-col justify-center md:border-r bl md:pr-12">
          <Label n="01">Security noise</Label>
          <div className="relative mt-8">
            <h2 className="cond invisible select-none text-[clamp(2.8rem,8vw,8.4rem)] leading-[0.86] font-[750] tracking-[-0.02em] uppercase" aria-hidden="true">
              Your security<br />stack sees<br />everything.
            </h2>
            <h2 className="cond absolute inset-0 text-[clamp(2.8rem,8vw,8.4rem)] leading-[0.86] font-[750] tracking-[-0.02em] uppercase"
              style={{ opacity: 1 - swap, filter: `blur(${swap * 12}px)`, transform: `translateY(${-swap * 40}px)` }}>
              Your security<br />stack sees<br />everything.
            </h2>
            <h2 className="cond absolute inset-0 text-[clamp(2.8rem,8vw,8.4rem)] leading-[0.86] font-[750] tracking-[-0.02em] uppercase"
              style={{ opacity: swap, filter: `blur(${(1 - swap) * 12}px)`, transform: `translateY(${(1 - swap) * 40}px)` }}>
              Your analysts<br /><span className="text-threat">can&apos;t.</span>
            </h2>
          </div>
          <div className="mt-10 hidden max-w-[440px] grid-cols-3 border-t bl pt-5 font-mono text-[10.5px] tracking-[0.14em] uppercase md:grid">
            {[['174', 'Events', 0], ['33', 'Alerts', 0.42], ['10', 'Incidents', 0.8]].map(([n, l, at]) => (
              <div key={l} className="transition-opacity duration-500" style={{ opacity: p >= at ? 1 : 0.25 }}>
                <div className="semi text-[26px] font-[600] tracking-normal">{n}</div>
                <div className="mute mt-1">{l}</div>
              </div>
            ))}
          </div>
        </div>

        <div className="relative flex items-center justify-center md:pl-12">
          <svg viewBox="0 0 600 600" className="h-auto w-full max-w-[min(560px,58vh)]" aria-label="174 events reduced to 33 alerts and 10 incidents">
            <rect x="0.5" y="0.5" width="599" height="599" fill="none" stroke="currentColor" strokeOpacity="0.12" />
            {events.map((e, i) => {
              const a = alerts[e.a]
              const tx = a.x + (e.j - 0.5) * 18 * (1 - gather), ty = a.y + ((e.j * 7) % 1 - 0.5) * 18 * (1 - gather)
              const show = clamp(seg(p, 0.0, 0.1) * 1.4 - (i / 174) * 0.4) * (1 - alertOn)
              return <circle key={i} cx={lerp(e.x, tx, gather)} cy={lerp(e.y, ty, gather)} r={1.9} fill="currentColor" opacity={show * 0.75} />
            })}
            {alerts.map((a, i) =>
              alerts.slice(i + 1).map((b, j) =>
                b.k === a.k ? (
                  <line key={`${i}-${j}`} x1={aPos[i].x} y1={aPos[i].y} x2={aPos[i + 1 + j].x} y2={aPos[i + 1 + j].y}
                    stroke="#73D7E8" strokeWidth="0.8" opacity={seg(p, 0.48, 0.58) * (1 - incOn)} />
                ) : null,
              ),
            )}
            {aPos.map((a, i) => (
              <g key={i} opacity={alertOn * (1 - incOn)}>
                <rect x={a.x - 4} y={a.y - 4} width="8" height="8" fill="none" stroke="currentColor" strokeWidth="1.2" />
                <rect x={a.x - 1.5} y={a.y - 1.5} width="3" height="3" fill="#73D7E8" />
              </g>
            ))}
            {inc.map((c, i) => (
              <g key={i} opacity={incOn} transform={`translate(${c.x} ${c.y})`}>
                <circle r={16 + (1 - incOn) * 20} fill="none" stroke="#C84D4D" strokeWidth="1.2" />
                <circle r="4" fill="#C84D4D" />
                <text x="24" y="4" fontFamily="JetBrains Mono" fontSize="10" fill="currentColor" letterSpacing="1">INC-{String(i + 1).padStart(2, '0')}</text>
              </g>
            ))}
          </svg>
          <div className="absolute top-6 right-0 text-right md:top-[14vh]">
            <div className="cond text-[clamp(4rem,9vw,8rem)] leading-none font-[700] tabular-nums">{count}</div>
            <div className="mute font-mono text-[10.5px] tracking-[0.2em] uppercase">{unit}</div>
          </div>
          <Resolve t={seg(p, 0.86, 0.96)} className="absolute bottom-8 left-0 md:left-12">
            <div className="flex items-baseline gap-4">
              <span className="cond text-[56px] leading-none font-[750]">94%</span>
              <span className="font-mono text-[10.5px] leading-snug tracking-[0.14em] uppercase">Reference-run<br />reduction</span>
            </div>
            <div className="mute mt-2 max-w-[300px] font-mono text-[10px] leading-relaxed">174 events → 10 incidents in one documented reference run. Results vary with data and tuning.</div>
          </Resolve>
        </div>
      </div>
    </section>
  )
}

/* ──────────── 02 PIPELINE ──────────── */
const STAGES = ['Ingest', 'Detect', 'Correlate', 'Risk', 'Investigate', 'Respond']
const SLOT = [0, 1, 2, 4, 5]
const RAW = ['AUTH_FAILURE', 'LOGIN_ATTEMPT', 'API_REQUEST', 'GEO_ANOMALY', 'PRIVILEGE_CHANGE']
const DETECTIONS = ['BRUTE_FORCE', 'CREDENTIAL_STUFFING', 'HIGH_FREQUENCY_API', 'SUSPICIOUS_LOGIN', 'IMPOSSIBLE_TRAVEL', 'ABNORMAL_ACCESS', 'HONEYTOKEN', 'BASELINE_DEVIATION']

function IngestPanel({ t }) {
  const rows = useMemo(() => {
    const r = rng(9)
    return Array.from({ length: 22 }, (_, i) => ({
      ts: `14:${String(2 + Math.floor(i / 3)).padStart(2, '0')}:${String(Math.floor(r() * 60)).padStart(2, '0')}.${Math.floor(r() * 900 + 100)}`,
      type: RAW[Math.floor(r() * RAW.length)],
      ip: `10.4.${Math.floor(r() * 255)}.${Math.floor(r() * 255)}`,
      d: r(),
    }))
  }, [])
  return (
    <div className="relative h-full overflow-hidden font-mono text-[11px] tracking-[0.06em]">
      <div className="absolute top-0 right-0 bottom-0 w-px bg-cyan/50" />
      <div className="absolute top-3 right-3 text-[9.5px] tracking-[0.2em] text-cyan">INGESTION LAYER</div>
      <div style={{ transform: `translateY(${-t * 280}px)` }}>
        {rows.map((r, i) => {
          const x = clamp(t * 1.6 - r.d * 0.6) * 100
          return (
            <div key={i} className="flex gap-5 py-[7px] whitespace-nowrap" style={{ transform: `translateX(${x}%)`, opacity: 1 - x / 110 }}>
              <span className="opacity-40">{r.ts}</span>
              <span className={r.type === 'GEO_ANOMALY' ? 'text-amber' : ''}>{r.type}</span>
              <span className="opacity-40">{r.ip}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function DetectPanel({ t }) {
  const fire = seg(t, 0.35, 0.6)
  return (
    <div className="grid h-full content-center gap-8">
      <div className="grid grid-cols-2 gap-px bg-white/10">
        {DETECTIONS.map((d) => {
          const hot = d === 'IMPOSSIBLE_TRAVEL'
          return (
            <div key={d} className="bg-[var(--bg)] px-4 py-3 font-mono text-[10.5px] tracking-[0.12em] transition-colors duration-500"
              style={{ color: hot && fire > 0.5 ? '#E9A23B' : undefined, opacity: hot ? 1 : 1 - fire * 0.55, outline: hot && fire > 0.5 ? '1px solid #E9A23B' : 'none' }}>
              {d}
            </div>
          )
        })}
      </div>
      <Resolve t={seg(t, 0.5, 0.75)} className="border-l-2 border-amber pl-5 font-mono text-[10.5px] tracking-[0.18em] uppercase">
        <div className="text-amber">● Rule triggered</div>
        <div className="semi mt-2 text-[28px] font-[650] tracking-[0.01em] normal-case">IMPOSSIBLE_TRAVEL</div>
        <div className="mt-3 flex gap-10"><span><span className="mute">Severity</span><br />HIGH</span><span><span className="mute">Window</span><br />2 logins · 2 countries</span></div>
      </Resolve>
    </div>
  )
}

const LATER = [
  ['10 alerts', '1 incident', 'Related alerts are grouped by entity and time.'],
  ['Evidence', 'Recommendation', 'The AI must cite what it found.'],
  ['Proposed', 'Approved', 'Actions wait for a human.'],
]

export function RiskPanel({ t }) {
  const FACTORS = [['Severity', 18], ['Frequency', 14], ['Repetition', 11], ['Asset criticality', 16], ['User behavior', 9], ['MITRE stage', 8], ['ML signal', 6]]
  const score = Math.round(FACTORS.reduce((s, [, v], i) => s + v * easeOut(seg(t, i / 8, (i + 1) / 8)), 0))
  return (
    <div className="grid gap-8 md:grid-cols-[auto_1fr] md:items-center">
      <div>
        <div className="cond text-[clamp(6rem,13vw,12rem)] leading-[0.8] font-[800] tracking-[-0.04em] tabular-nums">{String(score).padStart(2, '0')}</div>
        <div className="mt-3 flex items-center gap-3 font-mono text-[10.5px] tracking-[0.22em] uppercase">
          <span>Risk score</span><span className="mute">/ 100</span>
          <span className="px-2 py-0.5 text-[#0B0D0F]" style={{ background: score > 70 ? '#D98A32' : 'rgba(241,237,230,.3)' }}>{score > 70 ? 'High' : '···'}</span>
        </div>
      </div>
      <div className="hidden md:block">
        {FACTORS.map(([name, v], i) => {
          const on = easeOut(seg(t, i / 8, (i + 1) / 8))
          return (
            <div key={name} className="grid grid-cols-[1fr_44px] items-center gap-4 border-t border-white/10 py-2" style={{ opacity: 0.25 + on * 0.75 }}>
              <div>
                <div className="font-mono text-[10px] tracking-[0.16em] uppercase">{name}</div>
                <div className="mt-1.5 h-[2px] bg-white/[0.06]"><div className="h-full bg-amber/80" style={{ width: `${(v / 18) * 100 * on}%` }} /></div>
              </div>
              <div className="text-right font-mono text-[12px] tabular-nums">+{String(v).padStart(2, '0')}</div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export function Pipeline() {
  const [ref, p] = useTrack(0.2)
  const f = p * 6
  const active = Math.min(5, Math.floor(f))
  const local = f - active
  return (
    <section ref={ref} id="capabilities" className="relative h-[440vh]" data-bg="#0B0D0F" data-fg="#F1F0EB" data-ch="02 / PIPELINE">
      <div className="grid-lines pointer-events-none absolute inset-0 opacity-40" />
      <div className="sticky top-0 grid h-screen grid-rows-[auto_1fr] gap-6 overflow-hidden px-6 pt-24 pb-8 md:grid-cols-[0.9fr_1.1fr] md:grid-rows-1 md:gap-0 md:px-12 md:py-0">
        <div className="flex flex-col justify-center md:border-r bl md:pr-12">
          <Label n="02">The Sentinel pipeline</Label>
          <h2 className="cond mt-6 text-[clamp(2.6rem,6.5vw,6.8rem)] leading-[0.86] font-[750] tracking-[-0.02em] uppercase">From event<br />to action.</h2>
          <ol className="mt-8 grid grid-cols-3 gap-x-4 md:mt-12 md:block">
            {STAGES.map((s, i) => (
              <li key={s} className="flex items-baseline gap-5 border-t bl py-2 transition-all duration-500 md:py-3"
                style={{ opacity: i === active ? 1 : i < active ? 0.42 : 0.2 }}>
                <span className={`font-mono text-[10.5px] ${i === active ? 'text-cyan' : ''}`}>0{i + 1}</span>
                <span className={`semi uppercase transition-all duration-500 ${i === active ? 'text-[15px] font-[650] md:text-[30px]' : 'text-[13px] md:text-[18px]'}`}>{s}</span>
                {i === active && <span className="ml-auto hidden h-px flex-1 bg-cyan/50 md:block" style={{ transform: `scaleX(${local})`, transformOrigin: 'left' }} />}
              </li>
            ))}
          </ol>
        </div>
        <div className="relative min-h-0 md:pl-12">
          <div className="absolute inset-0 md:inset-y-[14vh] md:left-12" style={{ opacity: active === 0 ? 1 : 0, transition: 'opacity .5s' }}>
            <IngestPanel t={active === 0 ? local : 1} />
          </div>
          <div className="absolute inset-0 md:inset-y-[10vh] md:left-12" style={{ opacity: active === 1 ? 1 : 0, transition: 'opacity .5s' }}>
            <DetectPanel t={active === 1 ? local : active > 1 ? 1 : 0} />
          </div>
          <div className="absolute inset-0 flex flex-col justify-center md:left-12" style={{ opacity: active === 3 ? 1 : 0, transition: 'opacity .5s' }}>
            <RiskPanel t={active === 3 ? local : active > 3 ? 1 : 0} />
          </div>
          {LATER.map(([a, b, c], i) => (
            <div key={a} className="absolute inset-0 flex flex-col justify-center pr-6 md:left-12 md:pr-16" style={{ opacity: active === SLOT[i + 2] ? 1 : 0, transition: 'opacity .5s' }}>
              <div className="font-mono text-[10.5px] tracking-[0.2em] text-cyan uppercase">Stage 0{SLOT[i + 2] + 1}</div>
              <div className="cond mt-4 text-[clamp(2rem,3.8vw,4rem)] leading-tight font-[700] uppercase">
                <div className="opacity-40">{a}</div>
                <div className="flex items-baseline gap-3 mt-1">
                  <span className="text-cyan">→</span><span>{b}</span>
                </div>
              </div>
              <p className="mute mt-6 max-w-[380px] text-[15px]">{c}</p>
              <div className="mute mt-10 font-mono text-[10px] tracking-[0.2em] uppercase">Detail follows ↓</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
