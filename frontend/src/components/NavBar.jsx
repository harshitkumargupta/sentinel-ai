import { useEffect, useMemo, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { useTheme } from '../theme/ThemeProvider.jsx';
import CommandPalette from './CommandPalette.jsx';
import ThreatCoreLazy from './three/ThreatCoreLazy.jsx';

const NAV = [
  { to: '/dashboard', label: 'Dashboard', icon: '▦' },
  { to: '/events', label: 'Events', icon: '≋' },
  { to: '/alerts', label: 'Alerts', icon: '⚑' },
  { to: '/incidents', label: 'Incidents', icon: '✸' },
  { to: '/evaluation', label: 'Evaluation', icon: '✓' },
  { to: '/sites', label: 'Sites', icon: '⌂' },
  { to: '/admin', label: 'Admin', icon: '⚙', role: 'ADMIN' },
  { to: '/admin-risk', label: 'Admin Risk', icon: '⚖', role: 'ADMIN' },
  { to: '/pipeline', label: 'Pipeline', icon: '⇄', role: 'ADMIN' },
];

function readCollapsed() {
  try { return localStorage.getItem('sentinel.sidebarCollapsed') === '1'; } catch { return false; }
}

/**
 * App shell: a fixed collapsible, role-aware sidebar plus a top bar (command palette trigger, site
 * selector, notifications, theme + 3D-quality controls, user menu) and a Ctrl/Cmd+K command palette.
 * Rendered by each page at the top of `.app-shell`; the sidebar/top bar are fixed, and `.app-shell`
 * padding (ui.css) offsets the page content.
 */
export default function NavBar() {
  const { user, logout, hasRole } = useAuth();
  const { theme, toggleTheme, quality, setQuality } = useTheme();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [paletteOpen, setPaletteOpen] = useState(false);

  const links = useMemo(() => NAV.filter((n) => !n.role || hasRole(n.role)), [hasRole]);

  useEffect(() => {
    try { localStorage.setItem('sentinel.sidebarCollapsed', collapsed ? '1' : '0'); } catch { /* ignore */ }
  }, [collapsed]);

  useEffect(() => {
    const onKey = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setPaletteOpen((o) => !o);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  async function handleLogout() {
    await logout();
    navigate('/login');
  }

  return (
    <>
      <aside className={`shell-sidebar ${collapsed ? 'shell-sidebar--collapsed' : ''}`} aria-label="Primary">
        <div className="shell-brand">
          <ThreatCoreLazy size={28} />
          {!collapsed && <span className="brand">SentinelAI</span>}
        </div>
        <nav className="shell-nav">
          {links.map((n) => (
            <NavLink key={n.to} to={n.to} className="shell-navlink" title={n.label}>
              <span className="shell-navlink__icon" aria-hidden="true">{n.icon}</span>
              {!collapsed && <span>{n.label}</span>}
            </NavLink>
          ))}
        </nav>
        <button className="shell-collapse" onClick={() => setCollapsed((c) => !c)}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}>
          {collapsed ? '»' : '« Collapse'}
        </button>
      </aside>

      <header className="shell-topbar">
        <button className="shell-search" onClick={() => setPaletteOpen(true)} aria-label="Open command palette">
          <span aria-hidden="true">⌕</span> Search…
          <kbd className="shell-kbd">⌘K</kbd>
        </button>
        <div className="shell-topbar__right">
          <select className="shell-site" aria-label="Site selector" defaultValue="all">
            <option value="all">All sites</option>
          </select>
          <button className="ui-btn ui-btn--ghost ui-btn--icon" aria-label="Notifications" title="Notifications">🔔</button>
          <select className="shell-site" aria-label="3D quality" value={quality}
            onChange={(e) => setQuality(e.target.value)} title="3D visual quality">
            <option value="high">3D: High</option>
            <option value="low">3D: Low</option>
            <option value="off">3D: Off</option>
          </select>
          <button className="ui-btn ui-btn--ghost ui-btn--icon" onClick={toggleTheme}
            aria-label="Toggle theme" title="Toggle light/dark">{theme === 'dark' ? '☀' : '☾'}</button>
          <span className="user-email">{user?.username} <span className="role-chip">{user?.role}</span></span>
          <button className="ui-btn ui-btn--ghost ui-btn--sm" onClick={handleLogout}>Sign out</button>
        </div>
      </header>

      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} links={links} />
    </>
  );
}
