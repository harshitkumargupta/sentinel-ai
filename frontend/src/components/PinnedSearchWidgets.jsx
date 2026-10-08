import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { pinnedSearchStats } from '../services/search.service.js';
import { useLiveRefresh } from '../hooks/useLiveRefresh.js';

function Sparkline({ values }) {
  const max = Math.max(1, ...values);
  const w = 120;
  const h = 28;
  const step = w / Math.max(1, values.length - 1);
  const points = values.map((v, i) => `${(i * step).toFixed(1)},${(h - (v / max) * (h - 2) - 1).toFixed(1)}`).join(' ');
  return (
    <svg width={w} height={h} role="img" aria-label="Events per hour, last 24 hours">
      <polyline points={points} fill="none" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  );
}

/** Dashboard widgets for the user's pinned saved searches: count + 24h hourly trend, live. */
export default function PinnedSearchWidgets() {
  const navigate = useNavigate();
  const [widgets, setWidgets] = useState([]);
  const load = useCallback(() => { pinnedSearchStats().then(setWidgets).catch(() => setWidgets([])); }, []);
  useEffect(() => { load(); }, [load]);
  useLiveRefresh(load, 30000);

  if (widgets.length === 0) return null;
  return (
    <section className="panel">
      <h3>Pinned searches</h3>
      <div className="scenario-grid">
        {widgets.map((w) => (
          <button key={w.id} className="panel clickable" style={{ textAlign: 'left' }}
            onClick={() => navigate(`/search?saved=${w.id}`)} title="Open in Event Search">
            <div className="muted small">{w.name}</div>
            <div style={{ fontSize: '1.6rem', fontWeight: 700 }}>{w.count.toLocaleString()}</div>
            <Sparkline values={w.hourly} />
            <div className="muted small">last 24h, hourly</div>
          </button>
        ))}
      </div>
    </section>
  );
}
