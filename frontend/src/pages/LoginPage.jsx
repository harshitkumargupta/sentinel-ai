import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { getDemoInfo } from '../services/demo.service.js';
import LoginGlobeLazy from '../components/three/LoginGlobeLazy.jsx';
import './login.css';

const IS_DEV = import.meta.env.DEV;

const EyeIcon = ({ open }) =>
  open ? (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
      <path d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  ) : (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88" />
    </svg>
  );

const ShieldIcon = () => (
  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" />
  </svg>
);

const MicrosoftIcon = () => (
  <svg width="13" height="13" viewBox="0 0 21 21" aria-hidden="true">
    <rect x="1" y="1" width="9" height="9" fill="#f25022" />
    <rect x="11" y="1" width="9" height="9" fill="#7fba00" />
    <rect x="1" y="11" width="9" height="9" fill="#00a4ef" />
    <rect x="11" y="11" width="9" height="9" fill="#ffb900" />
  </svg>
);

const GoogleIcon = () => (
  <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden="true">
    <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4" />
    <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
    <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05" />
    <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
  </svg>
);

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();
  const [quick, setQuick] = useState([]);

  // Demo profile: server lists its public demo accounts for one-click sign-in (dev/demo only).
  useEffect(() => {
    getDemoInfo()
      .then((i) => setQuick(i.quickLogins || []))
      .catch(() => setQuick([]));
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
    <div className="lpv2-shell">
      {/* ── Top navigation ── */}
      <nav className="lpv2-nav" aria-label="Site navigation">
        <a href="/" className="lpv2-brand">
          SENTINEL<span className="lpv2-brand-accent">AI</span>
        </a>
        <div className="lpv2-navlinks" role="list">
          <a href="/#product" className="lpv2-navlink">Product</a>
          <a href="/#how-it-works" className="lpv2-navlink">How It Works</a>
          <a href="/#security" className="lpv2-navlink">Security</a>
          <a href="/#documentation" className="lpv2-navlink">Documentation</a>
        </div>
        <div className="lpv2-nav-right">
          <span>Not a member?</span>
          <a href="/register" className="lpv2-create-link">Create Account →</a>
        </div>
      </nav>

      {/* ── Main body ── */}
      <div className="lpv2-body">
        {/* ── LEFT: brand hero ── */}
        <div className="lpv2-left">
          <div>
            <p className="lpv2-eyebrow">AI-Powered Security Operations</p>
            <h1 className="lpv2-headline">
              TURN<br />
              SECURITY NOISE<br />
              INTO <span className="lpv2-headline-accent">DECISIONS.</span>
            </h1>
          </div>

          <p className="lpv2-support">
            Correlate, investigate and respond to real threats
            with AI-assisted security operations.
          </p>

          {/* Metrics */}
          <div className="lpv2-metrics" aria-label="Platform metrics">
            <div className="lpv2-metric">
              <span className="lpv2-metric-value">174</span>
              <span className="lpv2-metric-label">Events</span>
            </div>
            <div className="lpv2-metric-sep" aria-hidden="true" />
            <div className="lpv2-metric">
              <span className="lpv2-metric-value">33</span>
              <span className="lpv2-metric-label">Alerts</span>
            </div>
            <div className="lpv2-metric-sep" aria-hidden="true" />
            <div className="lpv2-metric">
              <span className="lpv2-metric-value">10</span>
              <span className="lpv2-metric-label">Incidents</span>
            </div>
            <div className="lpv2-metric-sep" aria-hidden="true" />
            <div className="lpv2-metric">
              <span className="lpv2-metric-value">94%</span>
              <span className="lpv2-metric-label">Response Reduction</span>
            </div>
          </div>

          {/* Command center panel */}
          <div className="lpv2-command" aria-label="Command center preview">
            <div className="lpv2-cmd-header" aria-hidden="true">
              <span className="lpv2-cmd-dot" style={{ background: '#ff5a5f' }} />
              <span className="lpv2-cmd-dot" style={{ background: '#f0b429' }} />
              <span className="lpv2-cmd-dot" style={{ background: '#2fbf61' }} />
              <span className="lpv2-cmd-title" style={{ marginLeft: '0.5rem' }}>Command Center</span>
            </div>
            <p className="lpv2-cmd-headline">
              From raw events<br />
              to explainable<br />
              decisions.
            </p>
            <div className="lpv2-cmd-stream" aria-hidden="true">
              <span className="lpv2-cmd-line lpv2-cmd-line--ok">✓ SIGMA rule matched — T1078</span>
              <span className="lpv2-cmd-line lpv2-cmd-line--alert">⚠ Anomalous auth 192.168.1.14</span>
              <span className="lpv2-cmd-line">EVENT 0x4625 — failed login ×3</span>
              <span className="lpv2-cmd-line lpv2-cmd-line--ok">✓ Correlated → INC-0042</span>
              <span className="lpv2-cmd-line lpv2-cmd-line--alert">⚠ Lateral movement detected</span>
              <span className="lpv2-cmd-line">PLAYBOOK triggered automatically</span>
            </div>
            <div className="lpv2-cmd-pipeline" aria-hidden="true">
              <span className="lpv2-cmd-stage lpv2-cmd-stage--active">Detection</span>
              <span className="lpv2-cmd-arrow">→</span>
              <span className="lpv2-cmd-stage lpv2-cmd-stage--active">Investigation</span>
              <span className="lpv2-cmd-arrow">→</span>
              <span className="lpv2-cmd-stage">Response</span>
            </div>
          </div>
        </div>

        {/* ── RIGHT: globe + login card ── */}
        <div className="lpv2-right">
          {/* Globe visual (decorative) */}
          <div className="lpv2-globe-wrap">
            <LoginGlobeLazy />
          </div>

          {/* Login card */}
          <div className="lpv2-card" role="main" aria-label="Sign in">
            <div className="lpv2-card-topline" aria-hidden="true" />
            <h2 className="lpv2-card-heading">Welcome back</h2>
            <p className="lpv2-card-sub">
              Sign in to your SentinelAI account
              and continue to the command center.
            </p>

            <form onSubmit={handleSubmit} noValidate>
              {/* Username */}
              <div className="lpv2-field">
                <label htmlFor="lpv2-username">Username</label>
                <input
                  id="lpv2-username"
                  type="text"
                  className="lpv2-input"
                  placeholder="Enter your username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  required
                  disabled={submitting}
                />
              </div>

              {/* Password */}
              <div className="lpv2-field">
                <label htmlFor="lpv2-password">Password</label>
                <div className="lpv2-field-wrap">
                  <input
                    id="lpv2-password"
                    type={showPassword ? 'text' : 'password'}
                    className="lpv2-input lpv2-input--pwd"
                    placeholder="Enter your password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete="current-password"
                    required
                    disabled={submitting}
                  />
                  <button
                    type="button"
                    className="lpv2-pwd-toggle"
                    onClick={() => setShowPassword((v) => !v)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    tabIndex={0}
                  >
                    <EyeIcon open={showPassword} />
                  </button>
                </div>
              </div>

              {/* Remember / Forgot */}
              <div className="lpv2-remember-row">
                <label className="lpv2-remember">
                  <input
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                  />
                  Remember me
                </label>
                {/* Forgot password is a placeholder — no dedicated route yet */}
                <span className="lpv2-forgot" style={{ cursor: 'default' }}>Forgot your password?</span>
              </div>

              {error && (
                <div className="lpv2-error" role="alert" aria-live="polite">
                  {error}
                </div>
              )}

              <button type="submit" className="lpv2-submit" disabled={submitting}>
                {submitting ? 'Signing in…' : 'Sign in →'}
              </button>
            </form>

            {/* OAuth divider */}
            <div className="lpv2-divider" aria-hidden="true">or continue with</div>

            {/* OAuth buttons — SSO not configured, rendered as visually unavailable */}
            <div className="lpv2-oauth">
              <button
                type="button"
                className="lpv2-oauth-btn"
                disabled
                title="Microsoft SSO is not configured in this environment"
                aria-disabled="true"
              >
                <MicrosoftIcon />
                Microsoft
              </button>
              <button
                type="button"
                className="lpv2-oauth-btn"
                disabled
                title="Google SSO is not configured in this environment"
                aria-disabled="true"
              >
                <GoogleIcon />
                Google
              </button>
            </div>

            {/* Security footer */}
            <div className="lpv2-security-footer">
              <ShieldIcon />
              <span>Secure access to SentinelAI · All logins are encrypted and audited.</span>
            </div>

            {/* Dev-only quick login — hidden in production */}
            {IS_DEV && quick.length > 0 && (
              <div className="lpv2-dev-quick" data-testid="dev-quick-login">
                <p className="lpv2-dev-label">Dev — quick sign in</p>
                <div className="lpv2-dev-btns">
                  {quick.map((a) => (
                    <button
                      key={a.username}
                      type="button"
                      className="lpv2-dev-btn"
                      disabled={submitting}
                      onClick={() => quickLogin(a)}
                    >
                      {a.role.charAt(0) + a.role.slice(1).toLowerCase()}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
