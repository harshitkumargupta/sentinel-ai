import { Component } from 'react'

export default class LandingErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    if (import.meta.env.DEV) {
      console.error('[LandingPage] Caught error:', error, info)
    }
  }

  render() {
    if (this.state.error) {
      return (
        <div className="landing-page" style={{ minHeight: '100svh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', background: '#050607', color: '#F1F0EB', padding: '2rem', textAlign: 'center' }}>
          <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: '10px', letterSpacing: '0.22em', textTransform: 'uppercase', opacity: 0.5, marginBottom: '2rem' }}>
            SENTINELAI
          </div>
          <h1 style={{ fontFamily: 'Archivo, system-ui, sans-serif', fontSize: 'clamp(2rem,6vw,5rem)', fontWeight: 750, textTransform: 'uppercase', lineHeight: 0.9, marginBottom: '1.5rem' }}>
            Turn Security Noise<br />Into <span style={{ color: '#73D7E8' }}>Decisions.</span>
          </h1>
          <p style={{ opacity: 0.55, maxWidth: '400px', lineHeight: 1.6, marginBottom: '2rem' }}>
            SentinelAI transforms raw security events into risk-scored incidents and controlled response actions.
          </p>
          <a href="/login" style={{ display: 'inline-flex', alignItems: 'center', gap: '1rem', background: '#F1F0EB', color: '#050607', padding: '1rem 1.75rem', fontFamily: 'JetBrains Mono, monospace', fontSize: '11px', letterSpacing: '0.22em', textTransform: 'uppercase', textDecoration: 'none' }}>
            Enter Command Center →
          </a>
        </div>
      )
    }
    return this.props.children
  }
}
