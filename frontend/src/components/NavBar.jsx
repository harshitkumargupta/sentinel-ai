import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

/**
 * Role-aware top navigation. Admin-only and analyst+ items are hidden for lesser roles
 * (the backend still enforces access; this is just UX).
 */
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
          <span className="nav-link active">Dashboard</span>
          <span className="nav-link muted">Events</span>
          <span className="nav-link muted">Incidents</span>
          {hasRole('ADMIN') && <span className="nav-link muted">Rules</span>}
          {hasRole('ADMIN') && <span className="nav-link muted">Users</span>}
          {hasRole('ADMIN') && <span className="nav-link muted">Audit</span>}
        </nav>
      </div>
      <div className="user-row">
        <span className="user-email">
          {user?.username} <span className="role-chip">{user?.role}</span>
        </span>
        <button className="ghost" onClick={handleLogout}>
          Sign out
        </button>
      </div>
    </header>
  );
}
