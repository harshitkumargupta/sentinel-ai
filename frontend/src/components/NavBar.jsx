import { useEffect, useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { useTheme } from '../theme/ThemeProvider.jsx';
import CommandPalette from './CommandPalette.jsx';
import ThreatCoreLazy from './three/ThreatCoreLazy.jsx';
import AiModeBadge from './AiModeBadge.jsx';
import NotificationBell from './NotificationBell.jsx';
import { getDemoInfo } from '../services/demo.service.js';

const A = ['ANALYST', 'ADMIN'];
// Grouped sidebar. `roles` = any of; `role` = exactly that role (via hasRole); demoOnly = demo profile only.
const NAV = [
  { group: 'Monitor', items: [
    { to: '/dashboard', label: 'Dashboard', icon: '▦' },
    { to: '/events', label: 'Events', icon: '≋' },
    { to: '/alerts', label: 'Alerts', icon: '⚑' },
    { to: '/offenses', label: 'Offenses', icon: '✸' },
  ] },
  { group: 'Investigate', items: [
    { to: '/incidents', label: 'Incidents / Cases', icon: '☰' },
    { to: '/search', label: 'Event Search', icon: '⌕' },
    { to: '/assets', label: 'Assets', icon: '▣' },
    { to: '/vulnerabilities', label: 'Vulnerabilities', icon: '⚠' },
    { to: '/reference-sets', label: 'Reference Sets', icon: '☷' },
  ] },
  { group: 'Respond', items: [
    { to: '/playbooks', label: 'Playbooks', icon: '⚡' },
    { to: '/honeytokens', label: 'Honeytokens', icon: '◎', roles: A },
    { to: '/notifications', label: 'Notifications', icon: '✉', role: 'ADMIN' },
  ] },
  { group: 'Configure', items: [
    { to: '/connect-website', label: 'Connect Website', icon: '⊕', role: 'ADMIN' },
    { to: '/sites', label: 'Sites', icon: '⌂' },
    { to: '/log-sources', label: 'Log Sources · Upload', icon: '⇲', roles: A },
    { to: '/rules', label: 'Rules', icon: '⚙︎' },
  ] },
  { group: 'Insights', items: [
    { to: '/executive', label: 'Executive', icon: '◔' },
    { to: '/coverage', label: 'Coverage', icon: '▤', roles: A },
    { to: '/reports', label: 'Reports', icon: '▤', roles: A },
    { to: '/evaluation', label: 'Evaluation', icon: '✓' },
  ] },
  { group: 'Admin', items: [
    { to: '/demo-center', label: 'Demo Center', icon: '▶', role: 'ADMIN', demoOnly: true },
    { to: '/audit', label: 'Audit Log', icon: '⛓', role: 'ADMIN' },
    { to: '/admin', label: 'Users & Settings', icon: '⚙', role: 'ADMIN' },
    { to: '/admin-risk', label: 'Risk Weights', icon: '⚖', role: 'ADMIN' },
    { to: '/pipeline', label: 'Pipeline', icon: '⇄', role: 'ADMIN' },
  ] },
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
  const { theme, toggleTheme, quality, setQuality, motion: motionPref, setMotion } = useTheme();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [paletteOpen, setPaletteOpen] = useState(false);

  const [demoMode, setDemoMode] = useState(false);
  useEffect(() => { getDemoInfo().then((i) => setDemoMode(Boolean(i.demoMode))).catch(() => {}); }, []);
  const groups = useMemo(
    () => NAV.map((g) => ({
      group: g.group,
      items: g.items.filter((n) => (!n.role || hasRole(n.role)) && (!n.roles || hasRole(...n.roles))
        && (!n.demoOnly || demoMode)),
    })).filter((g) => g.items.length > 0),
    [hasRole, demoMode],
  );
  const links = useMemo(() => groups.flatMap((g) => g.items), [groups]);

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
          {groups.map((g) => (
            <div key={g.group} className="shell-navgroup" role="group" aria-label={g.group}>
              {collapsed ? <hr className="shell-navgroup__rule" aria-hidden="true" />
                : <div className="shell-navgroup__title">{g.group}</div>}
          {g.items.map((n) => (
            <NavLink key={n.to} to={n.to} className="shell-navlink" title={n.label}>
              {({ isActive }) => (
                <>
                  {isActive && (
                    <motion.span layoutId="nav-pill" className="shell-navlink__pill" aria-hidden="true"
                      transition={{ type: 'spring', stiffness: 520, damping: 40 }} />
                  )}
                  <span className="shell-navlink__icon" aria-hidden="true">{n.icon}</span>
                  {!collapsed && <span className="shell-navlink__text">{n.label}</span>}
                </>
              )}
            </NavLink>
          ))}
            </div>
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
          <AiModeBadge />
          <select className="shell-site" aria-label="Site selector" defaultValue="all">
            <option value="all">All sites</option>
          </select>
          <NotificationBell />
          <select className="shell-site" aria-label="3D quality" value={quality}
            onChange={(e) => setQuality(e.target.value)} title="3D visual quality">
            <option value="high">3D: High</option>
            <option value="low">3D: Low</option>
            <option value="off">3D: Off</option>
          </select>
          <select className="shell-site" aria-label="Motion" value={motionPref}
            onChange={(e) => setMotion(e.target.value)} title="Interface motion">
            <option value="full">Motion: Full</option>
            <option value="reduced">Motion: Reduced</option>
            <option value="off">Motion: Off</option>
          </select>
          <button className="ui-btn ui-btn--ghost ui-btn--icon theme-toggle" onClick={toggleTheme}
            aria-label="Toggle theme" title="Toggle light/dark">
            <AnimatePresence mode="wait" initial={false}>
              <motion.span key={theme} aria-hidden="true"
                initial={{ rotate: -90, opacity: 0, scale: 0.5 }}
                animate={{ rotate: 0, opacity: 1, scale: 1 }}
                exit={{ rotate: 90, opacity: 0, scale: 0.5 }}
                transition={{ duration: 0.25 }}>
                {theme === 'dark' ? '☀' : '☾'}
              </motion.span>
            </AnimatePresence>
          </button>
          <span className="user-email">{user?.username} <span className="role-chip">{user?.role}</span></span>
          <button className="ui-btn ui-btn--ghost ui-btn--sm" onClick={handleLogout}>Sign out</button>
        </div>
      </header>

      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} links={links} />
    </>
  );
}
