import { useMemo, useState } from 'react';

const TYPE_COLOR = {
  incident: '#cf222e', alert: '#bc4c00', user: '#2f81f7', ip: '#1a7f37',
  resource: '#8250df', honeytoken: '#d4a72c', aggregate: '#57606a',
};
const TYPE_ICON = {
  incident: '🚨', alert: '🔔', user: '👤', ip: '🌐', resource: '📄', honeytoken: '🍯', aggregate: '⋯',
};
const COLUMN = { incident: 0, alert: 1, user: 2, ip: 3, resource: 4, honeytoken: 4, aggregate: 4 };

/**
 * Lightweight SVG storyline graph: nodes laid out by type, typed edges, click-to-inspect panel, a
 * kill-chain strip, and a time slider that replays how the attack unfolded.
 * (A richer force layout via cytoscape/react-force-graph can be dropped in later.)
 */
export default function StorylineGraph({ graph }) {
  const [selected, setSelected] = useState(null);
  const edges = graph?.edges ?? [];
  const times = edges.map((e) => new Date(e.firstSeen).getTime()).filter((n) => !Number.isNaN(n));
  const minT = times.length ? Math.min(...times) : 0;
  const maxT = times.length ? Math.max(...times) : 0;
  const [slider, setSlider] = useState(maxT);

  const pos = useMemo(() => {
    const byCol = {};
    const map = {};
    (graph?.nodes ?? []).forEach((n) => {
      const col = COLUMN[n.type] ?? 4;
      byCol[col] = (byCol[col] || 0) + 1;
      map[n.id] = { col, row: byCol[col] };
    });
    return { map, byCol };
  }, [graph]);

  if (!graph) return null;
  const W = 760;
  const colX = (c) => 80 + c * 150;
  const rowY = (r) => 60 + (r - 1) * 70;
  const visibleEdges = edges.filter((e) => new Date(e.firstSeen).getTime() <= slider);

  function xy(id) {
    const p = pos.map[id];
    return p ? { x: colX(p.col), y: rowY(p.row) } : { x: 0, y: 0 };
  }

  const height = Math.max(240, 60 + (Math.max(0, ...Object.values(pos.byCol)) * 70));

  return (
    <div>
      {maxT > minT && (
        <div className="filters">
          <label>Replay
            <input type="range" min={minT} max={maxT} value={slider}
                   onChange={(e) => setSlider(Number(e.target.value))} />
          </label>
          <span className="muted small">{new Date(slider).toLocaleString()}</span>
        </div>
      )}
      <p className="muted small" style={{ margin: '0 0 6px' }}>
        {selected ? `Focused on ${selected.type}: ${selected.label || selected.id} — click it again or Close to reset`
                  : 'Click a node to highlight its connections.'}
      </p>
      <div style={{ display: 'flex', gap: '1rem' }}>
        <svg width={W} height={height} style={{ border: '1px solid var(--border)', borderRadius: 8, maxWidth: '100%', background: 'var(--bg-elev)' }}>
          {visibleEdges.map((e, i) => {
            const a = xy(e.source); const b = xy(e.target);
            const active = selected && (e.source === selected.id || e.target === selected.id);
            // Edges fade to a faint grid by default; a selected node lights up only its own edges,
            // and only those show a label — so the view stays readable at any node count.
            const opacity = selected ? (active ? 0.95 : 0.05) : 0.18;
            return (
              <g key={i}>
                <line x1={a.x} y1={a.y} x2={b.x} y2={b.y}
                  stroke={active ? 'var(--accent)' : 'var(--border-strong)'}
                  strokeWidth={active ? 2 : 1} strokeOpacity={opacity} />
                {active && (
                  <text x={(a.x + b.x) / 2} y={(a.y + b.y) / 2 - 4} fontSize="10"
                    fill="var(--text)" textAnchor="middle"
                    style={{ paintOrder: 'stroke', stroke: 'var(--bg-elev)', strokeWidth: 3 }}>
                    {e.type}{e.count > 1 ? ` ×${e.count}` : ''}
                  </text>
                )}
              </g>
            );
          })}
          {(graph.nodes ?? []).map((n) => {
            const p = xy(n.id);
            const neighbor = selected && visibleEdges.some((e) =>
              (e.source === selected.id && e.target === n.id) || (e.target === selected.id && e.source === n.id));
            const dim = selected && n.id !== selected.id && !neighbor;
            return (
              <g key={n.id} transform={`translate(${p.x},${p.y})`} style={{ cursor: 'pointer', opacity: dim ? 0.2 : 1 }}
                 onClick={() => setSelected(selected && selected.id === n.id ? null : n)}>
                <circle r="16" fill={TYPE_COLOR[n.type] || '#57606a'}
                  stroke={selected && selected.id === n.id ? 'var(--accent)' : 'transparent'} strokeWidth="3" />
                <text textAnchor="middle" dy="4" fontSize="12">{TYPE_ICON[n.type] || '•'}</text>
                <text textAnchor="middle" y="28" fontSize="9" fill="var(--text)"
                  style={{ paintOrder: 'stroke', stroke: 'var(--bg-elev)', strokeWidth: 3 }}>
                  {(n.label || '').slice(0, 16)}
                </text>
              </g>
            );
          })}
        </svg>
        {selected && (
          <div className="panel" style={{ minWidth: 180 }}>
            <h4>{TYPE_ICON[selected.type]} {selected.type}</h4>
            <p className="small">{selected.label}</p>
            <p className="muted small">id: {selected.id}</p>
            <button className="ghost" onClick={() => setSelected(null)}>Close</button>
          </div>
        )}
      </div>

      <div className="killchain">
        {(graph.killChain ?? []).map((s) => (
          <div className="kc-stage" key={s.stage}>
            <span className="kc-num">{s.stage}</span>
            <span>{s.tactic}</span>
            <span className="muted small">{s.technique} ×{s.count}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
