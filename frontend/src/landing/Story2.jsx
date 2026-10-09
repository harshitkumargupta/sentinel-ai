import { useTrack, seg, ease, easeOut, lerp, Label, Resolve, clamp } from './lib'

/* ──────────── 05 AI INVESTIGATION ──────────── */
const EVIDENCE = ['Related authentication events', 'Geographic anomaly', 'Repeated failed attempts', 'Historical behavior deviation']

export function Investigation() {
  const [ref, p] = useTrack(1)
  const swap = seg(p, 0.18, 0.3)
  const step = seg(p, 0.3, 0.55)
  const ev = seg(p, 0.5, 0.78)
  const rec = seg(p, 0.78, 0.92)
  return (
    <section ref={ref} className="relative h-[320vh]" data-bg="#17140F" data-fg="#F1EDE6" data-ch="03 / INVESTIGATION">
      <div className="sticky top-0 h-screen overflow-hidden">
        <div className="pointer-events-none absolute -top-[20%] -left-[10%] h-[70vh] w-[60vw] rounded-full" style={{ background: 'radial-gradient(circle, rgba(217,138,50,0.13) 0%, transparent 70%)' }} />
        <div className="pointer-events-none absolute -right-[10%] -bottom-[20%] h-[70vh] w-[50vw] rounded-full" style={{ background: 'radial-gradient(circle, rgba(115,215,232,0.09) 0%, transparent 70%)' }} />
        <div className="relative grid h-full grid-rows-[auto_1fr] px-6 pt-24 md:grid-cols-[1fr_1fr] md:grid-rows-1 md:px-12 md:pt-0">
          <div className="flex flex-col justify-center md:pr-12">
            <Label n="03">AI investigation</Label>
            <div className="relative mt-8">
              <h2 className="cond invisible select-none text-[clamp(2.8rem,7.4vw,7.6rem)] leading-[0.86] font-[750] tracking-[-0.02em] uppercase" aria-hidden="true">
                It still has<br />to prove<br />its case.
              </h2>
              <h2 className="cond absolute inset-0 text-[clamp(2.8rem,7.4vw,7.6rem)] leading-[0.86] font-[750] tracking-[-0.02em] uppercase"
                style={{ opacity: 1 - swap, filter: `blur(${swap * 10}px)` }}>AI can<br />investigate.</h2>
              <h2 className="cond absolute inset-0 text-[clamp(2.8rem,7.4vw,7.6rem)] leading-[0.86] font-[750] tracking-[-0.02em] uppercase"
                style={{ opacity: swap, filter: `blur(${(1 - swap) * 10}px)` }}>It still has<br />to prove<br />its case.</h2>
            </div>
            <div className="mt-8 flex items-center gap-3 font-mono text-[10.5px] tracking-[0.2em] uppercase md:mt-12">
              {['Analyze', 'Correlate', 'Validate'].map((s, i) => {
                const on = step > i / 3
                return (
                  <div key={s} className="flex items-center gap-3">
                    <span className={`border px-3 py-2 transition-all duration-500 ${on ? 'border-[#F1EDE6] bg-[#F1EDE6] text-[#17140F]' : 'border-white/20 opacity-50'}`}>{s}</span>
                    {i < 2 && <span className="h-px w-6 bg-white/25" />}
                  </div>
                )
              })}
            </div>
          </div>
          <div className="relative flex flex-col justify-center pb-10 md:border-l md:border-white/[0.12] md:pb-0 md:pl-12">
            <div className="font-mono text-[10px] tracking-[0.22em] uppercase opacity-60">Evidence · INC-07 · risk 82</div>
            <ul className="mt-4">
              {EVIDENCE.map((e, i) => {
                const on = easeOut(seg(ev, i / 4, (i + 1) / 4))
                return (
                  <li key={e} className="relative flex items-center gap-5 border-t border-white/[0.12] py-4" style={{ opacity: 0.15 + on * 0.85, transform: `translateX(${(1 - on) * 24}px)` }}>
                    <span className="grid h-5 w-5 place-items-center border border-white/50 text-[11px]">{on > 0.6 ? '✓' : ''}</span>
                    <span className="semi text-[clamp(1.05rem,1.6vw,1.45rem)] font-[500]">{e}</span>
                    <span className="ml-auto font-mono text-[10px] opacity-40">E{i + 1}</span>
                    <span className="absolute top-1/2 -right-6 hidden h-px bg-cyan/60 md:block" style={{ width: `${rec * 24}px` }} />
                  </li>
                )
              })}
            </ul>
            <div className="relative mt-6 overflow-hidden border border-white/[0.12] bg-[#0B0D0F] p-6 text-[#F1F0EB]" style={{ clipPath: `inset(0 ${(1 - ease(rec)) * 100}% 0 0)` }}>
              <div className="font-mono text-[10px] tracking-[0.22em] text-cyan uppercase">Recommendation · cites E1–E4</div>
              <div className="semi mt-3 text-[clamp(1.2rem,2vw,1.7rem)] leading-tight font-[600]">Lock the affected account and force a credential reset.</div>
              <div className="mt-5 grid grid-cols-4 gap-2 font-mono text-[9px] tracking-[0.16em] uppercase">
                {['Proposed', 'Approved', 'Executed', 'Rolled back'].map((s, i) => (
                  <div key={s} className="border-t pt-2 transition-all duration-500" style={{ borderColor: rec > 0.4 + i * 0.15 && i < 2 ? '#73D7E8' : 'rgba(255,255,255,.15)', opacity: rec > 0.4 + i * 0.15 && i < 2 ? 1 : 0.4 }}>{s}</div>
                ))}
              </div>
              <div className="mt-4 font-mono text-[10px] tracking-[0.18em] uppercase opacity-50">AI recommends · a human approves · nothing runs silently</div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

/* ──────────── 07 ATTACK STORYLINE ──────────── */
const STORY = [
  { k: 'Entry', d: 'CREDENTIAL_STUFFING', t: '14:02:11', x: 0.06, y: 0.62 },
  { k: 'Authentication', d: 'SUSPICIOUS_LOGIN', t: '14:06:48', x: 0.26, y: 0.34 },
  { k: 'Location', d: 'IMPOSSIBLE_TRAVEL', t: '14:07:02', x: 0.44, y: 0.66 },
  { k: 'Access', d: 'ABNORMAL_ACCESS', t: '14:11:37', x: 0.62, y: 0.3 },
  { k: 'Tripwire', d: 'HONEYTOKEN', t: '14:12:05', x: 0.78, y: 0.58 },
  { k: 'Response', d: 'ACCOUNT_LOCK · APPROVED', t: '14:19:40', x: 0.94, y: 0.4 },
]

export function Storyline() {
  const [ref, p] = useTrack(1)
  const t = seg(p, 0.08, 0.88)
  const n = STORY.length
  return (
    <section ref={ref} className="relative h-[260vh]" data-bg="#08090B" data-fg="#F1F0EB" data-ch="04 / STORYLINE">
      <div className="sticky top-0 flex h-screen flex-col overflow-hidden px-6 pt-24 pb-10 md:px-12">
        <div className="flex flex-wrap items-end justify-between gap-6">
          <div>
            <Label n="04">Attack storyline</Label>
            <h2 className="cond mt-5 text-[clamp(2.4rem,6vw,6rem)] leading-[0.86] font-[750] tracking-[-0.02em] uppercase">One incident.<br />One story.</h2>
          </div>
          <div className="grid grid-cols-3 gap-8 font-mono text-[10px] tracking-[0.18em] uppercase">
            <div><div className="mute">Incident</div><div className="mt-1">INC-07</div></div>
            <div><div className="mute">Risk</div><div className="mt-1 text-amber">82 · High</div></div>
            <div><div className="mute">Alerts</div><div className="mt-1">{Math.max(1, Math.round(t * 5))} / 5</div></div>
          </div>
        </div>
        <div className="relative mt-6 min-h-0 flex-1">
          <svg viewBox="0 0 1000 400" preserveAspectRatio="none" className="absolute inset-0 h-full w-full">
            {STORY.slice(1).map((s, i) => {
              const a = STORY[i]
              const on = seg(t, (i + 0.5) / n, (i + 1) / n)
              return <line key={i} x1={a.x * 1000} y1={a.y * 400} x2={lerp(a.x, s.x, on) * 1000} y2={lerp(a.y, s.y, on) * 400}
                stroke={i === n - 2 ? '#7FD3E6' : '#E9A23B'} strokeOpacity="0.55" strokeWidth="1" vectorEffect="non-scaling-stroke" />
            })}
          </svg>
          {STORY.map((s, i) => {
            const on = easeOut(seg(t, i / n, (i + 0.5) / n))
            const last = i === n - 1
            return (
              <div key={s.k} className="absolute -translate-x-1/2 -translate-y-1/2" style={{ left: `${s.x * 100}%`, top: `${s.y * 100}%`, opacity: on, filter: `blur(${(1 - on) * 8}px)` }}>
                <div className={`mx-auto h-3 w-3 ${last ? 'bg-cyan' : 'border border-amber bg-[#0B0D0F]'}`} style={{ transform: `scale(${0.5 + on * 0.5})` }} />
                <div className={`absolute left-1/2 hidden w-max -translate-x-1/2 text-center md:block ${s.y > 0.5 ? 'top-6' : 'bottom-6'}`}>
                  <div className="font-mono text-[9.5px] tracking-[0.2em] opacity-50">{s.t}</div>
                  <div className="semi mt-1 text-[15px] font-[650] uppercase">{s.k}</div>
                  <div className={`font-mono text-[9.5px] tracking-[0.1em] ${last ? 'text-cyan' : 'text-amber'}`}>{s.d}</div>
                </div>
              </div>
            )
          })}
        </div>
        <div className="relative border-t border-white/15 pt-3">
          <div className="absolute -top-px left-0 h-px bg-[#F1F0EB]" style={{ width: `${t * 100}%` }} />
          <div className="flex justify-between font-mono text-[9.5px] tracking-[0.16em] uppercase">
            {STORY.map((s, i) => <span key={s.k} className="transition-opacity" style={{ opacity: t >= i / n ? 0.9 : 0.25 }}><span className="md:hidden">{s.k.slice(0, 4)}</span><span className="hidden md:inline">{s.t} · {s.k}</span></span>)}
          </div>
        </div>
        <div className="mute mt-3 font-mono text-[9.5px] tracking-[0.12em]">Illustrative sequence built only from supported detection types.</div>
      </div>
    </section>
  )
}

/* ──────────── 08 PRODUCT — enter the command center ──────────── */
const MITRE = ['Initial Access', 'Execution', 'Persistence', 'Priv. Esc.', 'Defense Ev.', 'Cred. Access', 'Discovery', 'Lateral Mv.']

function Dashboard() {
  const heat = [0, 1, 0, 2, 0, 4, 1, 0, 1, 0, 0, 3, 1, 4, 2, 1, 0, 0, 1, 0, 0, 2, 3, 2]
  return (
    <div className="grid h-full grid-cols-12 grid-rows-[auto_1fr_1fr] gap-px bg-white/[0.07] text-[#F1F0EB]">
      {[['Events · 24h', '174'], ['Alerts', '33'], ['Incidents', '10'], ['Pending approval', '2']].map(([l, v], i) => (
        <div key={l} className="col-span-6 bg-[#0B0D0F] p-3 md:col-span-3 md:p-4">
          <div className="font-mono text-[9px] tracking-[0.18em] uppercase opacity-50">{l}</div>
          <div className={`semi mt-1 text-[clamp(1.2rem,2.4vw,2rem)] font-[600] ${i === 3 ? 'text-amber' : ''}`}>{v}</div>
        </div>
      ))}
      <div className="col-span-12 bg-[#0B0D0F] p-4 md:col-span-7">
        <div className="font-mono text-[9px] tracking-[0.18em] uppercase opacity-50">MITRE ATT&amp;CK coverage</div>
        <div className="mt-3 grid grid-cols-8 gap-1">
          {heat.map((h, i) => <div key={i} className="aspect-[2/1]" style={{ background: h ? `rgba(233,162,59,${h * 0.2})` : 'rgba(255,255,255,0.04)' }} />)}
        </div>
        <div className="mt-2 hidden grid-cols-8 gap-1 font-mono text-[7.5px] tracking-[0.05em] uppercase opacity-40 md:grid">{MITRE.map((m) => <span key={m} className="truncate">{m}</span>)}</div>
      </div>
      <div className="relative hidden overflow-hidden bg-[#0B0D0F] p-4 md:col-span-5 md:block">
        <div className="font-mono text-[9px] tracking-[0.18em] uppercase opacity-50">ThreatCore</div>
        <div className="absolute inset-0 grid place-items-center">
          <div className="relative h-28 w-28">
            {[0, 1, 2].map((i) => <div key={i} className="absolute inset-0 rounded-full border border-white/15" style={{ transform: `scale(${1 - i * 0.28})` }} />)}
            <div className="absolute inset-[38%] rounded-full bg-amber/70 blur-[6px]" />
          </div>
        </div>
      </div>
      <div className="col-span-12 bg-[#0B0D0F] p-4 font-mono text-[10px] md:col-span-8">
        <div className="text-[9px] tracking-[0.18em] uppercase opacity-50">Live events</div>
        {['AUTH_FAILURE  10.4.18.22', 'GEO_ANOMALY   10.4.91.7', 'API_REQUEST   10.4.3.160', 'LOGIN_ATTEMPT 10.4.18.22'].map((e, i) => (
          <div key={i} className="mt-2 flex gap-4 opacity-80"><span className="opacity-40">14:1{i}:0{i * 2}</span><span className={i === 1 ? 'text-amber' : ''}>{e}</span></div>
        ))}
      </div>
      <div className="hidden bg-[#0B0D0F] p-4 md:col-span-4 md:block">
        <div className="font-mono text-[9px] tracking-[0.18em] uppercase opacity-50">Detection tuning</div>
        {[['BRUTE_FORCE', 'threshold 5 / 60s'], ['HIGH_FREQUENCY_API', 'threshold 120 / 60s'], ['IMPOSSIBLE_TRAVEL', 'enabled']].map(([a, b]) => (
          <div key={a} className="mt-2 flex justify-between border-t border-white/5 pt-2 font-mono text-[9.5px]"><span>{a}</span><span className="opacity-40">{b}</span></div>
        ))}
      </div>
    </div>
  )
}

export function Product() {
  const [ref, p] = useTrack(1)
  const t = ease(seg(p, 0, 0.7))
  return (
    <section ref={ref} className="relative h-[220vh]" data-bg="#050607" data-fg="#F1F0EB" data-ch="05 / COMMAND CENTER">
      <div className="sticky top-0 flex h-screen flex-col items-center justify-center overflow-hidden px-4 md:px-10">
        <Resolve t={1 - seg(p, 0.3, 0.5)} className="absolute top-24 left-6 md:left-12">
          <Label n="05">The command center</Label>
          <h2 className="cond mt-5 text-[clamp(2.4rem,5.5vw,5.5rem)] leading-[0.86] font-[750] uppercase">Step inside.</h2>
        </Resolve>
        <div className="w-full max-w-[1320px] border border-white/15 bg-[#07080A] shadow-[0_60px_120px_-40px_rgba(0,0,0,0.8)]"
          style={{ transform: `perspective(1600px) translateY(${(1 - t) * 18}vh) rotateX(${(1 - t) * 18}deg) scale(${lerp(0.62, 1, t)})`, opacity: lerp(0.35, 1, t) }}>
          <div className="flex items-center gap-4 border-b border-white/10 px-4 py-2.5 font-mono text-[10px] tracking-[0.14em]">
            <span className="flex gap-1.5">{[0, 1, 2].map((i) => <span key={i} className="h-2 w-2 rounded-full bg-white/15" />)}</span>
            <span className="opacity-40">sentinel.local / dashboard</span>
            <span className="ml-auto flex items-center gap-2 text-cyan"><span className="blink h-1.5 w-1.5 rounded-full bg-cyan" />LIVE</span>
          </div>
          <div className="h-[min(62vh,640px)]"><Dashboard /></div>
        </div>
      </div>
    </section>
  )
}
