import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import LoginBackdropLazy from '../components/three/LoginBackdropLazy.jsx';
import { getDemoInfo } from '../services/demo.service.js';

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();
  const [quick, setQuick] = useState([]);

  // Demo profile only: the server lists its public demo accounts for one-click sign-in.
  useEffect(() => {
    getDemoInfo().then((i) => setQuick(i.quickLogins || [])).catch(() => setQuick([]));
  }, []);

  async function quickLogin(account) {
    setError(null);
    setSubmitting(true);
    try {
      await login(account.username, account.password);
      navigate('/dashboard');
    } catch (err) {
      setError(err?.response?.data?.error?.message || 'Login failed.');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(username, password);
      navigate('/dashboard');
    } catch (err) {
      const msg = err?.response?.data?.error?.message || 'Login failed. Check your credentials.';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-shell" style={{ position: 'relative', overflow: 'hidden' }}>
      <LoginBackdropLazy />
      <form className="auth-card" onSubmit={handleSubmit} style={{ position: 'relative', zIndex: 1 }}>
        <h1 className="brand">🛡️ SentinelAI</h1>
        <p className="subtitle">Mini Security Operations Center</p>

        <label htmlFor="username">Username</label>
        <input
          id="username"
          type="text"
          placeholder="analyst"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          required
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          placeholder="••••••••"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          required
        />

        {error && <p className="error-text">{error}</p>}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
        {quick.length > 0 ? (
          <div className="quick-login">
            <p className="hint">Demo mode — sign in as:</p>
            <div className="filters">
              {quick.map((a) => (
                <button key={a.username} type="button" className="ghost" disabled={submitting} onClick={() => quickLogin(a)}>
                  Login as {a.role.charAt(0) + a.role.slice(1).toLowerCase()}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <p className="hint">Sign in with your SentinelAI account.</p>
        )}
      </form>
    </div>
  );
}
