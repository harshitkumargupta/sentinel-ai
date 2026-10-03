import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

/**
 * Placeholder login page for Phase 0/1. It does not authenticate against the
 * backend yet — it just drops the user into the dashboard so the shell is
 * navigable. Real auth arrives in the auth phase.
 */
export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();
  const { login } = useAuth();

  function handleSubmit(e) {
    e.preventDefault();
    login({ email: email || 'analyst@sentinel.ai', role: 'ANALYST' });
    navigate('/dashboard');
  }

  return (
    <div className="auth-shell">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1 className="brand">🛡️ SentinelAI</h1>
        <p className="subtitle">Mini Security Operations Center</p>

        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          placeholder="analyst@sentinel.ai"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="username"
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          placeholder="••••••••"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
        />

        <button type="submit">Sign in</button>
        <p className="hint">Placeholder — authentication is wired up in a later phase.</p>
      </form>
    </div>
  );
}
