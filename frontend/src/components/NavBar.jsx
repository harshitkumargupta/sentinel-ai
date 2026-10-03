import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

/** Role-aware top navigation (the backend still enforces access; this is just UX). */
export default function NavBar() {
  const { user, logout, hasRole } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login');
  }

  return (
    <header className="topbar">
      <div className="brand-row">
        <span className="brand">🛡️ SentinelAI</span>
        <nav className="nav-links">
          <NavLink to="/dashboard" className="nav-link">Dashboard</NavLink>
          <NavLink to="/events" className="nav-link">Events</NavLink>
          <NavLink to="/alerts" className="nav-link">Alerts</NavLink>
          <NavLink to="/incidents" className="nav-link">Incidents</NavLink>
          <NavLink to="/evaluation" className="nav-link">Evaluation</NavLink>
          <NavLink to="/sites" className="nav-link">Sites</NavLink>
          {hasRole('ADMIN') && <NavLink to="/admin" className="nav-link">Admin</NavLink>}
          {hasRole('ADMIN') && <NavLink to="/admin-risk" className="nav-link">Admin&nbsp;Risk</NavLink>}
        </nav>
      </div>
      <div className="user-row">
        <span className="user-email">
          {user?.username} <span className="role-chip">{user?.role}</span>
        </span>
        <button className="ghost" onClick={handleLogout}>Sign out</button>
      </div>
    </header>
  );
}
