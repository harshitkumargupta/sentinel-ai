import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { useHealth } from '../hooks/useHealth.js';
import StatusBadge from '../components/StatusBadge.jsx';

const PLACEHOLDER_TILES = [
  { label: 'Open Incidents', value: '—' },
  { label: 'Events (24h)', value: '—' },
  { label: 'Active Rules', value: '—' },
  { label: 'Highest Risk', value: '—' },
];

export default function DashboardPage() {
  const { user, logout } = useAuth();
  const { status, detail } = useHealth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand-row">
          <span className="brand">🛡️ SentinelAI</span>
          <StatusBadge status={status} />
        </div>
        <div className="user-row">
          <span className="user-email">{user?.email || 'guest'}</span>
          <button className="ghost" onClick={handleLogout}>
            Sign out
          </button>
        </div>
      </header>

      <main className="content">
        <h2>SOC Overview</h2>
        <p className="subtitle">
          Placeholder dashboard — live metrics arrive once events and incidents land (Phase 2+).
        </p>

        <section className="tiles">
          {PLACEHOLDER_TILES.map((t) => (
            <div className="tile" key={t.label}>
              <span className="tile-value">{t.value}</span>
              <span className="tile-label">{t.label}</span>
            </div>
          ))}
        </section>

        <section className="panel">
          <h3>Backend health</h3>
          <pre className="code-block">
            {status === 'up' ? JSON.stringify(detail, null, 2) : `status: ${status}`}
          </pre>
        </section>
      </main>
    </div>
  );
}
