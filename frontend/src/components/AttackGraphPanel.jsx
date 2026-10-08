import { Suspense, lazy, useEffect, useMemo, useRef, useState } from 'react';
import { useTheme } from '../theme/ThemeProvider.jsx';
import { hasWebGL, useCanvasActive } from '../three/webgl.js';
import ThreeErrorBoundary from '../three/ThreeErrorBoundary.jsx';
import { SEVERITY_COLOR, buildAttackGraph, layoutAttackGraph } from '../three/attackGraphModel.js';

const AttackGraph3D = lazy(() => import('../three/AttackGraph3D.jsx'));
const STEPS = 100;

/** 2D SVG rendering of the same graph — the fallback on weak devices / no WebGL / 3D off. */
function AttackGraph2D({ nodes, edges, positions, selectedId, onSelect }) {
  const W = 640;
  const H = Math.max(220, 70 * Math.max(...['ip', 'user', 'host'].map((t) => nodes.filter((n) => n.type === t).length), 1));
  const xy = (id) => { const [x, y] = positions[id] || [0, 0]; return [W / 2 + x * (W * 0.36), H / 2 + y * 60]; };
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="Attack graph">
      {edges.map((e) => { const [x1, y1] = xy(e.source); const [x2, y2] = xy(e.target);
        return <line key={e.id} x1={x1} y1={y1} x2={x2} y2={y2} stroke={SEVERITY_COLOR[e.severity]} strokeWidth={1 + Math.min(5, Math.log2(1 + e.count))} opacity={0.8} />; })}
      {nodes.map((n) => { const [x, y] = xy(n.id); const c = SEVERITY_COLOR[n.severity];
        return (
          <g key={n.id} onClick={() => onSelect(n)} style={{ cursor: 'pointer' }}>
            {n.type === 'user' ? <rect x={x - 11} y={y - 11} width={22} height={22} fill={c} stroke={n.id === selectedId ? '#fff' : 'none'} strokeWidth={2} />
              : n.type === 'host' ? <polygon points={`${x},${y - 13} ${x + 13},${y} ${x},${y + 13} ${x - 13},${y}`} fill={c} stroke={n.id === selectedId ? '#fff' : 'none'} strokeWidth={2} />
              : <circle cx={x} cy={y} r={12} fill={c} stroke={n.id === selectedId ? '#fff' : 'none'} strokeWidth={2} />}
            <text x={x} y={y + 27} textAnchor="middle" fontSize="11" fill="currentColor">{n.label}</text>
          </g>
        ); })}
    </svg>
  );
}

/**
 * Attack graph + replay for an incident: IPs, users and hosts from its events, edges for attack steps,
 * coloured by severity. Slider / Play reveal nodes and edges in time order; click a node for details.
 * Uses Three.js when available and enabled, otherwise (or on error) the 2D SVG view.
 */
export default function AttackGraphPanel({ events }) {
  const graph = useMemo(() => buildAttackGraph(events || []), [events]);
  const positions = useMemo(() => layoutAttackGraph(graph.nodes), [graph]);
  const { effectiveQuality } = useTheme();
  const { ref, active } = useCanvasActive();
  const can3d = effectiveQuality !== 'off' && hasWebGL();
  const [mode, setMode] = useState(can3d ? '3d' : '2d');
  const [step, setStep] = useState(STEPS);
  const [playing, setPlaying] = useState(false);
  const [selected, setSelected] = useState(null);
  const timer = useRef(null);

  useEffect(() => {
    if (!playing) return undefined;
    timer.current = setInterval(() => setStep((s) => { if (s >= STEPS) { setPlaying(false); return STEPS; } return s + 2; }), 80);
    return () => clearInterval(timer.current);
  }, [playing]);

  if (graph.nodes.length === 0) return null;
  const span = (graph.end ?? 0) - (graph.start ?? 0);
  const cutoff = (graph.start ?? 0) + (span * step) / STEPS;
  const nodes = graph.nodes.filter((n) => n.firstSeen <= cutoff);
  const edges = graph.edges.filter((e) => e.firstSeen <= cutoff && nodes.some((n) => n.id === e.source) && nodes.some((n) => n.id === e.target));
  const props = { nodes, edges, positions, selectedId: selected?.id, onSelect: setSelected };
  const twoD = <AttackGraph2D {...props} />;

  return (
    <section className="panel">
      <div className="brand-row" style={{ justifyContent: 'space-between' }}>
        <h3>Attack graph</h3>
        <div className="filters">
          <button className="ghost" onClick={() => { if (step >= STEPS) setStep(0); setPlaying((p) => !p); }}>{playing ? '⏸ Pause' : '▶ Play'}</button>
          {can3d && <button className="ghost" aria-pressed={mode === '3d'} onClick={() => setMode(mode === '3d' ? '2d' : '3d')}>{mode === '3d' ? '2D view' : '3D view'}</button>}
        </div>
      </div>
      <label className="small" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>Replay
        <input type="range" min={0} max={STEPS} value={step} onChange={(e) => { setPlaying(false); setStep(Number(e.target.value)); }} style={{ flex: 1 }} aria-label="Replay position" />
        <span className="muted">{graph.start ? new Date(cutoff).toLocaleTimeString() : ''} · {nodes.length}/{graph.nodes.length} entities</span>
      </label>
      <div ref={ref} style={{ height: mode === '3d' ? 380 : 'auto' }}>
        {mode === '3d' && can3d ? (
          <ThreeErrorBoundary fallback={twoD}>
            <Suspense fallback={<div className="ui-skel" style={{ height: 380 }} />}>
              <AttackGraph3D {...props} quality={effectiveQuality} active={active} />
            </Suspense>
          </ThreeErrorBoundary>
        ) : twoD}
      </div>
      <p className="muted small">● IP · ■ user · ◆ host — colour = highest severity ({Object.keys(SEVERITY_COLOR).join(' < ')}). Drag to rotate in 3D.</p>
      {selected && (
        <div className="panel">
          <strong>{selected.type.toUpperCase()} {selected.label}</strong> <span className="chip" style={{ background: SEVERITY_COLOR[selected.severity] }}>{selected.severity}</span>
          <div className="small">{selected.count} event(s): {selected.types.join(', ')}</div>
          <div className="muted small">First {new Date(selected.firstSeen).toLocaleString()} · last {new Date(selected.lastSeen).toLocaleString()}</div>
          <div className="small">Connected: {graph.edges.filter((e) => e.source === selected.id || e.target === selected.id)
            .map((e) => (e.source === selected.id ? e.target : e.source).split(':').slice(1).join(':')).join(', ') || '—'}</div>
        </div>
      )}
    </section>
  );
}
